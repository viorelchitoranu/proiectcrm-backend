package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.*;
import com.springapp.proiectcrm.exception.BusinessException;
import com.springapp.proiectcrm.exception.ErrorCode;
import com.springapp.proiectcrm.logging.MdcFilter;
import com.springapp.proiectcrm.model.Child;
import com.springapp.proiectcrm.model.ChildGroup;
import com.springapp.proiectcrm.model.User;
import com.springapp.proiectcrm.repository.ChildGroupRepository;
import com.springapp.proiectcrm.repository.ChildRepository;
import com.springapp.proiectcrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Serviciu pentru operațiunile admin pe părinți (Modulele 2, 3, 4).
 *
 * Logging:
 *   CHANGE_EMAIL_OK      → INFO după schimbare email reușită (oldEmail mascat GDPR)
 *   DEACTIVATE_PARENT_OK → INFO cu numărul de locuri de grupă eliberate
 *   ACTIVATE_PARENT_OK   → INFO la reactivare
 *
 * Operațiile de START sunt logate de AuditLoggingAspect.java (AOP).
 * Erorile de business sunt interceptate de GlobalExceptionHandler.
 *
 * Diferență față de dezactivarea per-copil (AdminChildService):
 *   Dezactivarea PĂRINTELUI → dezactivează ChildGroup-urile TUTUROR copiilor
 *   Dezactivarea COPILULUI  → dezactivează ChildGroup-urile unui singur copil
 *   Cele două mecanisme sunt independente și complementare.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminParentServiceImpl implements AdminParentService {

    private final UserRepository       userRepository;
    private final ChildRepository      childRepository;
    private final ChildGroupRepository childGroupRepository;
    private final EmailService         emailService;

    // ══════════════════════════════════════════════════════════════════════════
    // Helper privat
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Găsește un User după ID și verifică că are rol PARENT.
     * Folosit în toate metodele care au nevoie de un părinte valid.
     */
    private User requireParent(int parentId) {
        User parent = userRepository.findById(parentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PARENT_NOT_FOUND,
                        "Părintele cu id " + parentId + " nu există."));
        if (!"PARENT".equalsIgnoreCase(parent.getRole().getRoleName())) {
            throw new BusinessException(
                    ErrorCode.USER_NOT_PARENT_ROLE,
                    "Utilizatorul cu id " + parentId + " nu are rol de PARENT.");
        }
        return parent;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Listare părinți paginată
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public AdminPagedResponse<AdminParentSummaryResponse> getParents(String query, int page, int size) {
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 5), 100);
        var pageable = PageRequest.of(p, s);
        Page<AdminParentSummaryResponse> result = userRepository.findParentsPaged(query, pageable);
        return new AdminPagedResponse<>(
                result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Profil detaliat părinte
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public AdminParentDetailsResponse getParentDetails(int parentId) {
        User parent = userRepository.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Părintele nu a fost găsit."));

        if (!"PARENT".equalsIgnoreCase(parent.getRole().getRoleName())) {
            throw new IllegalArgumentException("Utilizatorul nu are rol de PARENT.");
        }

        List<Child> children = childRepository.findByParent(parent);

        List<AdminChildRowResponse> childRows = children.stream().map(c -> {
            ChildGroup cg = childGroupRepository
                    .findTopByChildAndActiveTrueOrderByEnrollmentDateDesc(c)
                    .orElse(null);
            return new AdminChildRowResponse(
                    c.getIdChild(), c.getChildFirstName(), c.getChildLastName(),
                    c.getAge(), c.getSchool(), c.getSchoolClass(),
                    parent.getIdUser(),
                    parent.getLastName() + " " + parent.getFirstName(),
                    parent.getEmail(), parent.getPhone(),
                    cg != null ? cg.getGroup().getIdGroup()   : null,
                    cg != null ? cg.getGroup().getGroupName() : null,
                    cg != null ? cg.getEnrollmentDate()       : null,
                    // câmp nou: starea individuală a copilului (null = activ, compatibilitate veche)
                    c.getActive()
            );
        }).toList();

        AdminParentSummaryResponse parentDto = new AdminParentSummaryResponse(
                parent.getIdUser(), parent.getFirstName(), parent.getLastName(),
                parent.getEmail(), parent.getPhone(), (long) children.size(), parent.getActive());

        return new AdminParentDetailsResponse(parentDto, childRows);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Modul 2: Adăugare copil la un părinte existent
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public AdminChildRowResponse addChildToParent(int parentId, AdminAddChildRequest request) {
        User parent = requireParent(parentId);

        Child child = new Child();
        child.setParent(parent);
        child.setChildFirstName(request.getFirstName().trim());
        child.setChildLastName(request.getLastName().trim());
        child.setAge(request.getAge());
        child.setSchool(request.getSchool() != null ? request.getSchool().trim() : null);
        child.setSchoolClass(request.getSchoolClass() != null ? request.getSchoolClass().trim() : null);
        child.setChildCreatedAt(java.time.LocalDateTime.now());
        child.setActive(true);  // DEFAULT 1 din BD, dar setăm explicit pentru claritate

        Child saved = childRepository.save(child);

        log.info("ADD_CHILD_OK parentId={} childId={} childName=\"{} {}\"",
                parentId, saved.getIdChild(), saved.getChildFirstName(), saved.getChildLastName());

        return new AdminChildRowResponse(
                saved.getIdChild(), saved.getChildFirstName(), saved.getChildLastName(),
                saved.getAge(), saved.getSchool(), saved.getSchoolClass(),
                parent.getIdUser(),
                parent.getLastName() + " " + parent.getFirstName(),
                parent.getEmail(), parent.getPhone(),
                null, null, null,  // fără grupă activă — copilul nou nu e înscris nicăieri
                saved.getActive()
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Modul 3: Schimbare email
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Schimbă email-ul de login al unui părinte.
     *
     * Logging GDPR: loghăm ambele emailuri MASCATE.
     * Nu loghăm emailul în clar — chiar și în loguri de server trebuie respectat GDPR.
     * Emailul mascat (ion***@***.ro) este suficient pentru a confirma că schimbarea a avut loc
     * și pentru a o corela cu audit-ul, fără a expune datele personale.
     */
    @Override
    @Transactional
    public AdminParentSummaryResponse changeParentEmail(int parentId, AdminChangeEmailRequest request) {
        User parent = requireParent(parentId);

        String newEmail = request.getNewEmail().trim().toLowerCase(Locale.ROOT);
        String oldEmail = parent.getEmail();

        if (oldEmail.equalsIgnoreCase(newEmail)) {
            throw new BusinessException(ErrorCode.EMAIL_SAME_AS_CURRENT,
                    "Noul email este identic cu email-ul curent.");
        }

        userRepository.findByEmail(newEmail).ifPresent(existing -> {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS,
                    "Există deja un cont cu email-ul " + newEmail + ".");
        });

        parent.setEmail(newEmail);
        User saved = userRepository.save(parent);

        // GDPR: ambele emailuri mascate — confirmăm că schimbarea a avut loc fără a expune date
        log.info("CHANGE_EMAIL_OK parentId={} oldEmail={} newEmail={}",
                parentId,
                MdcFilter.maskEmail(oldEmail),
                MdcFilter.maskEmail(newEmail));

        String fullName = saved.getFirstName() + " " + saved.getLastName();
        sendEmailChangedAsync(oldEmail, newEmail, fullName);

        long childCount = childRepository.findByParent(saved).size();
        return new AdminParentSummaryResponse(
                saved.getIdUser(), saved.getFirstName(), saved.getLastName(),
                saved.getEmail(), saved.getPhone(), childCount, saved.getActive());
    }

    /**
     * Trimite emailurile de notificare asincron, în afara tranzacției principale.
     * Dacă mail server-ul e down → tranzacția BD nu se rollback-uiește.
     */
    @Async
    protected void sendEmailChangedAsync(String oldEmail, String newEmail, String parentName) {
        try {
            emailService.sendEmailChangedNotification(oldEmail, newEmail, parentName);
        } catch (Exception e) {
            // Eroarea de email nu trebuie să blocheze operația — loghăm ca WARN
            log.warn("EMAIL_NOTIFICATION_FAILED event=emailChanged parentName=\"{}\" error=\"{}\"",
                    parentName, e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Modul 4: Dezactivare / reactivare cont PARINTE
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Dezactivează contul unui PĂRINTE + eliberează locurile TUTUROR copiilor săi.
     * AuditLoggingAspect loghează DEACTIVATE_PARENT_START.
     * Noi loghăm DEACTIVATE_PARENT_OK cu totalReleased — util pentru audit.
     *
     * NOTA: child.active NU este atins — dezactivarea per-copil e separată.
     * Dezactivarea părintelui dezactivează DOAR ChildGroup-urile (locuri).
     */
    @Override
    @Transactional
    public ParentActivationResponse deactivateParent(int parentId) {
        User parent = requireParent(parentId);

        if (Boolean.FALSE.equals(parent.getActive())) {
            throw new BusinessException(ErrorCode.PARENT_ALREADY_INACTIVE,
                    "Contul părintelui este deja dezactivat.");
        }

        List<Child> children = childRepository.findByParent(parent);
        int totalReleased = 0;

        for (Child child : children) {
            List<ChildGroup> activeEnrollments = childGroupRepository.findByChildAndActiveTrue(child);
            if (!activeEnrollments.isEmpty()) {
                activeEnrollments.forEach(cg -> cg.setActive(false));
                childGroupRepository.saveAll(activeEnrollments);
                totalReleased += activeEnrollments.size();
            }
        }

        parent.setActive(false);
        User saved = userRepository.save(parent);

        // INFO cu numărul total de locuri eliberate — util pentru audit și statistici
        log.info("DEACTIVATE_PARENT_OK parentId={} email={} childrenCount={} slotsReleased={}",
                parentId,
                MdcFilter.maskEmail(saved.getEmail()),
                children.size(),
                totalReleased);

        sendDeactivatedAsync(saved, totalReleased);

        return new ParentActivationResponse(
                saved.getIdUser(), saved.getLastName() + " " + saved.getFirstName(),
                false, totalReleased, LocalDateTime.now(),
                "Contul a fost dezactivat. " + totalReleased + " înscriere(i) eliberate.");
    }

    /**
     * Reactivează contul unui PĂRINTE.
     * Setează NUMAI parent.active=true — ChildGroup-urile NU sunt recreate automat.
     * Re-înscrierea fiecărui copil se face manual de admin.
     */
    @Override
    @Transactional
    public ParentActivationResponse activateParent(int parentId) {
        User parent = requireParent(parentId);

        if (Boolean.TRUE.equals(parent.getActive())) {
            throw new BusinessException(ErrorCode.PARENT_ALREADY_ACTIVE,
                    "Contul părintelui este deja activ.");
        }

        parent.setActive(true);
        User saved = userRepository.save(parent);

        log.info("ACTIVATE_PARENT_OK parentId={} email={}",
                parentId, MdcFilter.maskEmail(saved.getEmail()));

        sendReactivatedAsync(saved);

        return new ParentActivationResponse(
                saved.getIdUser(), saved.getLastName() + " " + saved.getFirstName(),
                true, 0, LocalDateTime.now(),
                "Contul a fost reactivat. Copiii trebuie re-înscriși manual în grupe.");
    }

    @Async
    protected void sendDeactivatedAsync(User parent, int released) {
        try {
            emailService.sendParentDeactivated(parent, released);
        } catch (Exception e) {
            log.warn("EMAIL_NOTIFICATION_FAILED event=parentDeactivated parentId={} error=\"{}\"",
                    parent.getIdUser(), e.getMessage());
        }
    }

    @Async
    protected void sendReactivatedAsync(User parent) {
        try {
            emailService.sendParentReactivated(parent);
        } catch (Exception e) {
            log.warn("EMAIL_NOTIFICATION_FAILED event=parentReactivated parentId={} error=\"{}\"",
                    parent.getIdUser(), e.getMessage());
        }
    }
}
