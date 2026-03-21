package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.EnrollmentChildRequest;
import com.springapp.proiectcrm.dto.EnrollmentItemResponse;
import com.springapp.proiectcrm.dto.EnrollmentRequest;
import com.springapp.proiectcrm.dto.EnrollmentResponse;
import com.springapp.proiectcrm.exception.BusinessException;
import com.springapp.proiectcrm.exception.ErrorCode;
import com.springapp.proiectcrm.logging.LogSanitizer;
import com.springapp.proiectcrm.logging.MdcFilter;
import com.springapp.proiectcrm.model.Child;
import com.springapp.proiectcrm.model.ChildGroup;
import com.springapp.proiectcrm.model.GroupClass;
import com.springapp.proiectcrm.model.Role;
import com.springapp.proiectcrm.model.User;
import com.springapp.proiectcrm.repository.ChildGroupRepository;
import com.springapp.proiectcrm.repository.ChildRepository;
import com.springapp.proiectcrm.repository.GroupClassRepository;
import com.springapp.proiectcrm.repository.RoleRepository;
import com.springapp.proiectcrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Serviciu de înscriere publică — creează cont de părinte + copii + înregistrări în grupe.
 *
 * Ce loghează această clasă:
 *   ENROLL_START   → INFO la începutul fluxului (emailul mascat GDPR)
 *   ENROLL_SUCCESS → INFO la finalizare cu succes (parentId, număr copii, grupe)
 *   ENROLL_BLOCKED → WARN când înscrierea eșuează din motive de business
 *                    (grupă plină, email duplicat etc.)
 *
 * Logul ENROLL_START + ENROLL_SUCCESS cu același requestId (din MDC) permite
 * corelarea celor două evenimente și calcularea duratei de procesare.
 *
 * Logul ENROLL_BLOCKED este util pentru a identifica:
 *   - grupe populare care se umplu frecvent (candidat pentru capacitate mărită)
 *   - tentative de înscriere cu email duplicat (poate indica confuzie utilizator)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnrollmentServiceImpl implements EnrollmentService {

    private final UserRepository          userRepository;
    private final RoleRepository          roleRepository;
    private final ChildRepository         childRepository;
    private final GroupClassRepository    groupClassRepository;
    private final ChildGroupRepository    childGroupRepository;
    private final PasswordEncoder         passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    private static final String PARENT_ROLE_NAME = "PARENT";

    /**
     * Orchestrează întregul flux de înscriere publică. Apelează 8 metode private în ordine:
     *   1. validateChildrenPayload()        → 1–4 copii, fără duplicate
     *   2. ensureParentEmailAvailable()     → emailul nu există deja în BD
     *   3. aggregateRequestedSeatsByGroup() → Map<groupId, locuri cerute>
     *   4. Locking pesimist                 → SELECT FOR UPDATE, IDs sortate (anti-deadlock)
     *   5. validateLockedGroupsAndCapacity()→ grupă activă, nedepășită, nedepășit endDate
     *   6. createParentUser()               → User cu rol PARENT, parolă BCrypt
     *   7. Loop copii                       → createChild + ensureNotAlreadyEnrolled + createEnrollment
     *   8. publishEvent()                   → EnrollmentCompletedEvent (email AFTER_COMMIT)
     */
    @Override
    @Transactional
    public EnrollmentResponse enrollChildren(EnrollmentRequest request) {

        // INFO la start — emailul mascat GDPR, numărul de copii
        // Acest log apare ÎNAINTEA oricărei validări — util dacă înscrierea eșuează
        // și vrem să vedem că a fost inițiată
        log.info("ENROLL_START email={} children={}",
                LogSanitizer.sanitize(MdcFilter.maskEmail(request.getParentEmail())),
                request.getChildren() != null ? request.getChildren().size() : 0);

        try {
            validateChildrenPayload(request);
            ensureParentEmailAvailable(request.getParentEmail());

            Map<Integer, Integer> requestedSeatsByGroup = aggregateRequestedSeatsByGroup(request.getChildren());

            // Locking pesimist: IDs sortate crescător pentru a preveni deadlock-uri
            // când două cereri concurente cer aceleași grupe în ordine inversă
            List<Integer> groupIds = new ArrayList<>(requestedSeatsByGroup.keySet());
            Collections.sort(groupIds);
            List<GroupClass> lockedGroups = groupClassRepository.findAllByIdGroupInForUpdate(groupIds);
            Map<Integer, GroupClass> groupById = mapGroupsById(lockedGroups);

            validateLockedGroupsAndCapacity(groupIds, groupById, requestedSeatsByGroup);

            String rawPassword = request.getParentPassword();
            User parent = createParentUser(request);

            List<EnrollmentItemResponse> items = new ArrayList<>();
            for (EnrollmentChildRequest childReq : request.getChildren()) {
                GroupClass group = groupById.get(childReq.getGroupId());
                Child child = createChild(childReq, parent);
                ensureNotAlreadyEnrolled(child, group);
                createEnrollment(child, group);
                items.add(new EnrollmentItemResponse(
                        child.getIdChild(), group.getIdGroup(), group.getGroupName(),
                        child.getChildFirstName(), child.getChildLastName()
                ));
            }

            eventPublisher.publishEvent(new EnrollmentCompletedEvent(
                    parent.getEmail(), parent.getFirstName(), parent.getLastName(),
                    rawPassword, List.copyOf(items)
            ));

            // INFO la succes — parentId util pentru căutare ulterioară în BD
            log.info("ENROLL_SUCCESS email={} parentId={} childrenCount={} groups={}",
                    LogSanitizer.sanitize(MdcFilter.maskEmail(request.getParentEmail())),
                    parent.getIdUser(),
                    items.size(),
                    items.stream().map(i -> String.valueOf(i.getGroupId())).toList());

            return new EnrollmentResponse(parent.getIdUser(), items, "Înscrierea a fost realizată cu succes.");

        } catch (BusinessException e) {
            // WARN: înscrierea a eșuat din motive de business (grupă plină, email duplicat etc.)
            // Nu re-aruncăm — GlobalExceptionHandler va prinde BusinessException și va loga din nou
            // Dar loghăm ENROLL_BLOCKED explicit ca eveniment de audit
            log.warn("ENROLL_BLOCKED email={} reason={} message=\"{}\"",
                    LogSanitizer.sanitize(MdcFilter.maskEmail(request.getParentEmail())),
                    e.getErrorCode().name(),
                    LogSanitizer.sanitize(e.getMessage()));
            throw e;  // re-aruncăm pentru a fi gestionată de GlobalExceptionHandler
        }
    }

    // ── Validare payload ──────────────────────────────────────────────────────

    private void validateChildrenPayload(EnrollmentRequest request) {
        List<EnrollmentChildRequest> children = request.getChildren();
        if (children == null || children.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_ENROLLMENT_REQUEST,
                    "Trebuie să existe cel puțin un copil în cerere.");
        }
        if (children.size() > 4) {
            throw new BusinessException(ErrorCode.TOO_MANY_CHILDREN_IN_REQUEST,
                    "Poți înscrie maximum 4 copii într-o singură cerere.");
        }
        Set<String> seen = new HashSet<>();
        for (EnrollmentChildRequest c : children) {
            String key = normalize(c.getChildLastName()) + "|" + normalize(c.getChildFirstName());
            if (!seen.add(key)) {
                throw new BusinessException(ErrorCode.DUPLICATE_CHILD_IN_REQUEST,
                        "Același copil apare de două ori în formular.");
            }
        }
    }

    private String normalize(String v) {
        return v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
    }

    private void ensureParentEmailAvailable(String email) {
        userRepository.findByEmail(email).ifPresent(existing -> {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS,
                    "Există deja un cont cu acest email. Te rugăm să te autentifici și să înscrii copilul din contul tău.");
        });
    }

    // ── Capacitate grupe ──────────────────────────────────────────────────────

    /**
     * Reduce lista de copii la Map<groupId, locuri cerute>.
     * Dacă doi copii merg în aceeași grupă → contorizăm 2 locuri.
     */
    private Map<Integer, Integer> aggregateRequestedSeatsByGroup(List<EnrollmentChildRequest> children) {
        Map<Integer, Integer> result = new HashMap<>();
        for (EnrollmentChildRequest child : children) {
            result.merge(child.getGroupId(), 1, Integer::sum);
        }
        return result;
    }

    private Map<Integer, GroupClass> mapGroupsById(List<GroupClass> groups) {
        Map<Integer, GroupClass> result = new HashMap<>();
        for (GroupClass g : groups) {
            result.put(g.getIdGroup(), g);
        }
        return result;
    }

    /**
     * Validează fiecare grupă din request:
     *   - Există în BD (a fost găsită cu FOR UPDATE)
     *   - isActive != FALSE
     *   - endDate == null SAU endDate >= azi
     *   - Locuri disponibile >= locuri cerute (0 = nelimitat)
     */
    private void validateLockedGroupsAndCapacity(
            List<Integer> orderedGroupIds,
            Map<Integer, GroupClass> groupById,
            Map<Integer, Integer> requestedSeatsByGroup
    ) {
        LocalDate today = LocalDate.now();
        for (Integer groupId : orderedGroupIds) {
            GroupClass group = groupById.get(groupId);
            if (group == null) {
                throw new BusinessException(ErrorCode.GROUP_NOT_FOUND, "Grupa cu id " + groupId + " nu există.");
            }
            if (Boolean.FALSE.equals(group.getIsActive())) {
                throw new BusinessException(ErrorCode.GROUP_INACTIVE, "Grupa " + group.getGroupName() + " nu este activă.");
            }
            if (group.getGroupEndDate() != null && today.isAfter(group.getGroupEndDate())) {
                throw new BusinessException(ErrorCode.GROUP_ENDED, "Grupa " + group.getGroupName() + " s-a încheiat deja.");
            }
            int seatsRequested = requestedSeatsByGroup.getOrDefault(groupId, 0);
            if (group.getGroupMaxCapacity() == 0) continue;  // 0 = nelimitat
            long active = childGroupRepository.countByGroupAndActiveTrue(group);
            int remaining = Math.max(0, group.getGroupMaxCapacity() - (int) active);
            if (remaining < seatsRequested) {
                throw new BusinessException(ErrorCode.GROUP_FULL,
                        "Grupa " + group.getGroupName() + " are doar " + remaining +
                                " loc(uri) disponibile, dar cererea solicită " + seatsRequested + ".");
            }
        }
    }

    // ── Creare entități ───────────────────────────────────────────────────────

    private User createParentUser(EnrollmentRequest request) {
        Role parentRole = roleRepository.findByRoleName(PARENT_ROLE_NAME)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARENT_NOT_FOUND,
                        "Rolul PARENT nu există în baza de date."));
        User newParent = new User();
        newParent.setFirstName(request.getParentFirstName());
        newParent.setLastName(request.getParentLastName());
        newParent.setEmail(request.getParentEmail());
        newParent.setPhone(request.getParentPhone());
        newParent.setAddress(request.getParentAddress());
        newParent.setRole(parentRole);
        newParent.setCreatedAt(LocalDate.now());
        newParent.setActive(true);   // ← lipsea
        newParent.setPassword(passwordEncoder.encode(request.getParentPassword()));
        return userRepository.save(newParent);
    }

    private Child createChild(EnrollmentChildRequest request, User parent) {
        Child child = new Child();
        child.setParent(parent);
        child.setChildLastName(request.getChildLastName());
        child.setChildFirstName(request.getChildFirstName());
        child.setAge(request.getChildAge());
        child.setSchool(request.getChildSchool());
        child.setSchoolClass(request.getChildSchoolClass());
        child.setChildCreatedAt(LocalDateTime.now());
        child.setActive(true);
        return childRepository.save(child);
    }

    private void ensureNotAlreadyEnrolled(Child child, GroupClass group) {
        childGroupRepository.findByChildAndGroup(child, group).ifPresent(existing -> {
            if (Boolean.TRUE.equals(existing.getActive())) {
                throw new BusinessException(ErrorCode.CHILD_ALREADY_ENROLLED,
                        "Copilul " + child.getChildFirstName() + " " + child.getChildLastName() +
                                " este deja înscris în grupa " + group.getGroupName() + ".");
            } else {
                throw new BusinessException(ErrorCode.CHILD_HAS_INACTIVE_ENROLLMENT,
                        "Copilul " + child.getChildFirstName() + " " + child.getChildLastName() +
                                " are deja un istoric de înscriere în grupa " + group.getGroupName() + ".");
            }
        });
    }

    private ChildGroup createEnrollment(Child child, GroupClass group) {
        ChildGroup childGroup = new ChildGroup();
        childGroup.setChild(child);
        childGroup.setGroup(group);
        childGroup.setActive(true);
        childGroup.setEnrollmentDate(LocalDate.now());
        return childGroupRepository.save(childGroup);
    }
}
