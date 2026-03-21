package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.*;
import com.springapp.proiectcrm.exception.BusinessException;
import com.springapp.proiectcrm.exception.ErrorCode;
import com.springapp.proiectcrm.logging.LogSanitizer;
import com.springapp.proiectcrm.model.*;
import com.springapp.proiectcrm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Implementare serviciu pentru lista de așteptare.
 *
 * Flux public (addToWaitlist):
 *   1. Normalizare email (lowercase, trim)
 *   2. Guard unicitate: același email nu poate fi WAITING de două ori
 *   3. Creare WaitlistEntry cu status WAITING + createdAt = now()
 *   4. Return DTO cu datele salvate
 *
 * Flux admin (allocate):
 *   1. Găsire entry + verificare status WAITING
 *   2. Găsire grupă + verificare activă + verificare capacitate
 *   3. Dacă emailul NU există în BD → creare cont User (PARENT) cu parolă random
 *      Dacă emailul EXISTĂ → refolosire cont existent
 *   4. Creare Child legat de User
 *   5. Creare ChildGroup (enrollment) cu status active = true
 *   6. Marcare entry: status = ALLOCATED, allocatedAt = now(), allocatedGroup = group
 *   7. Trimitere email async (nu blocăm tranzacția BD pentru un email)
 *
 * Generare parolă:
 *   UUID random de 8 caractere — suficient pentru parola temporară.
 *   Părintele o poate schimba ulterior din contul său.
 *
 * Logging:
 *   WAITLIST_ADD      → INFO la adăugare pe lista de așteptare (email mascat GDPR)
 *   WAITLIST_ALLOCATE → INFO la alocare reușită (entryId, groupId, newAccount)
 *   WAITLIST_CANCEL   → INFO la anulare
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WaitlistServiceImpl implements WaitlistService {

    private final WaitlistRepository    waitlistRepository;
    private final UserRepository        userRepository;
    private final RoleRepository        roleRepository;
    private final ChildRepository       childRepository;
    private final ChildGroupRepository  childGroupRepository;
    private final GroupClassRepository  groupClassRepository;
    private final PasswordEncoder       passwordEncoder;
    private final EmailService          emailService;

    private static final String PARENT_ROLE_NAME = "PARENT";

    // ── Adăugare pe lista de așteptare (public) ───────────────────────────────

    /**
     * Înregistrează o cerere nouă pe lista de așteptare.
     *
     * Guard unicitate: un email poate fi WAITING O SINGURĂ dată.
     * Dacă emailul e deja ALLOCATED sau CANCELLED, o nouă cerere WAITING este permisă
     * (ex: părintele a fost alocat, a renunțat, și vrea să se înscrie din nou).
     */
    @Override
    @Transactional
    public WaitlistEntryResponse addToWaitlist(WaitlistRequest request) {
        // GDPR: normalizăm emailul înainte de orice operație
        String email = request.getParentEmail().trim().toLowerCase(Locale.ROOT);

        // Guard: același email nu poate fi WAITING de două ori simultan
        if (waitlistRepository.existsByParentEmailAndStatus(email, WaitlistStatus.WAITING)) {
            throw new BusinessException(
                    ErrorCode.WAITLIST_ALREADY_REGISTERED,
                    "Există deja o cerere activă (în așteptare) pentru emailul " + email
                            + ". Vei fi contactat de echipa noastră.");
        }

        WaitlistEntry entry = new WaitlistEntry();
        entry.setParentFirstName(request.getParentFirstName().trim());
        entry.setParentLastName(request.getParentLastName().trim());
        entry.setParentEmail(email);
        entry.setParentPhone(request.getParentPhone() != null ? request.getParentPhone().trim() : null);
        entry.setParentAddress(request.getParentAddress() != null ? request.getParentAddress().trim() : null);

        entry.setChildFirstName(request.getChildFirstName().trim());
        entry.setChildLastName(request.getChildLastName().trim());
        entry.setChildAge(request.getChildAge());
        entry.setChildSchool(request.getChildSchool());
        entry.setChildSchoolClass(request.getChildSchoolClass());

        entry.setPreferredCourseName(request.getPreferredCourseName());
        entry.setPreferredSchoolName(request.getPreferredSchoolName());
        entry.setNotes(request.getNotes());

        entry.setStatus(WaitlistStatus.WAITING);
        entry.setCreatedAt(LocalDateTime.now());

        WaitlistEntry saved = waitlistRepository.save(entry);

        // GDPR: emailul mascat în log
        log.info("WAITLIST_ADD entryId={} email={} child=\"{} {}\" course=\"{}\" school=\"{}\"",
                saved.getId(),
                LogSanitizer.sanitize(maskEmail(email)),
                LogSanitizer.sanitize(saved.getChildFirstName()),
                LogSanitizer.sanitize(saved.getChildLastName()),
                LogSanitizer.sanitize(saved.getPreferredCourseName()),
                LogSanitizer.sanitize(saved.getPreferredSchoolName()));

        return toResponse(saved);
    }

    // ── Listare pentru admin ──────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<WaitlistEntryResponse> getAllEntries() {
        return waitlistRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Alocare (admin) ───────────────────────────────────────────────────────

    /**
     * Alocă un copil din waitlist la o grupă specificată de admin.
     *
     * Dacă emailul părintelui NU există în BD → creare cont nou cu parolă random.
     * Dacă emailul părintelui EXISTĂ în BD   → refolosire cont + adăugare copil nou.
     *
     * În ambele cazuri: se creează Child + ChildGroup și se trimite email de notificare.
     */
    @Override
    @Transactional
    public WaitlistAllocateResponse allocate(int entryId, WaitlistAllocateRequest request) {
        WaitlistEntry entry = waitlistRepository.findById(entryId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.WAITLIST_ENTRY_NOT_FOUND,
                        "Cererea cu id " + entryId + " nu a fost găsită."));

        // Guard: se poate aloca doar o cerere în așteptare
        if (entry.getStatus() != WaitlistStatus.WAITING) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Cererea nu mai este în așteptare (status: " + entry.getStatus() + ").");
        }

        // ── Validare grupă ────────────────────────────────────────────────────
        GroupClass group = groupClassRepository.findById(request.getGroupId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.GROUP_NOT_FOUND,
                        "Grupa cu id " + request.getGroupId() + " nu a fost găsită."));

        if (Boolean.FALSE.equals(group.getIsActive())) {
            throw new BusinessException(ErrorCode.GROUP_INACTIVE,
                    "Grupa " + group.getGroupName() + " nu este activă.");
        }

        // Verificare capacitate (0 = nelimitat)
        if (group.getGroupMaxCapacity() > 0) {
            long activeCount = childGroupRepository.countByGroupAndActiveTrue(group);
            if (activeCount >= group.getGroupMaxCapacity()) {
                throw new BusinessException(ErrorCode.GROUP_FULL,
                        "Grupa " + group.getGroupName() + " este plină ("
                                + activeCount + "/" + group.getGroupMaxCapacity() + " locuri).");
            }
        }

        // ── Creare sau reutilizare cont părinte ───────────────────────────────
        String rawPassword = null;
        boolean newAccountCreated = false;

        User parent = userRepository.findByEmail(entry.getParentEmail()).orElse(null);

        if (parent == null) {
            // Emailul nu există → creem cont nou cu parolă generată random
            // UUID de 8 caractere: suficient pentru parolă temporară
            rawPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

            Role parentRole = roleRepository.findByRoleName(PARENT_ROLE_NAME)
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.PARENT_NOT_FOUND, "Rolul PARENT nu există în BD."));

            parent = new User();
            parent.setFirstName(entry.getParentFirstName());
            parent.setLastName(entry.getParentLastName());
            parent.setEmail(entry.getParentEmail());
            parent.setPhone(entry.getParentPhone());
            parent.setAddress(entry.getParentAddress());
            parent.setRole(parentRole);
            parent.setCreatedAt(LocalDate.now());
            parent.setActive(true);
            parent.setPassword(passwordEncoder.encode(rawPassword));
            parent = userRepository.save(parent);
            newAccountCreated = true;
        }

        // ── Creare copil ──────────────────────────────────────────────────────
        Child child = new Child();
        child.setParent(parent);
        child.setChildFirstName(entry.getChildFirstName());
        child.setChildLastName(entry.getChildLastName());
        child.setAge(entry.getChildAge() != null ? entry.getChildAge() : 0);
        child.setSchool(entry.getChildSchool());
        child.setSchoolClass(entry.getChildSchoolClass());
        child.setChildCreatedAt(LocalDateTime.now());
        child.setActive(true);
        child = childRepository.save(child);
        final Child savedChild = child;  // ← adaugă această linie

        // ── Creare enrollment ─────────────────────────────────────────────────
        // orElseGet: previne duplicate dacă există deja o înregistrare inactivă
        ChildGroup enrollment = childGroupRepository
                .findByChildAndGroup(savedChild, group)  // ← folosește savedChild
                .orElseGet(() -> {
                    ChildGroup cg = new ChildGroup();
                    cg.setChild(savedChild);             // ← folosește savedChild
                    cg.setGroup(group);
                    return cg;
                });
        enrollment.setActive(true);
        enrollment.setEnrollmentDate(LocalDate.now());
        childGroupRepository.save(enrollment);

        // ── Marcare entry ca ALLOCATED ────────────────────────────────────────
        entry.setStatus(WaitlistStatus.ALLOCATED);
        entry.setAllocatedAt(LocalDateTime.now());
        entry.setAllocatedGroup(group);
        waitlistRepository.save(entry);

        log.info("WAITLIST_ALLOCATE entryId={} email={} childId={} groupId={} newAccount={}",
                entryId, maskEmail(entry.getParentEmail()),
                savedChild.getIdChild(), group.getIdGroup(), newAccountCreated);


        // ── Email async — nu blocăm tranzacția pentru livrarea emailului ──────
        final User finalParent   = parent;
        final String finalRaw    = rawPassword;
        final boolean finalIsNew = newAccountCreated;
        sendAllocationEmailAsync(finalParent, finalRaw, group.getGroupName(), finalIsNew);

        String childName = savedChild.getChildLastName() + " " + savedChild.getChildFirstName();
        return new WaitlistAllocateResponse(
                entryId,
                parent.getIdUser(),
                parent.getEmail(),
                savedChild.getIdChild(),
                childName,
                group.getIdGroup(),
                group.getGroupName(),
                newAccountCreated,
                newAccountCreated
                        ? "Cont creat și copil înscris în grupa " + group.getGroupName() + ". Email trimis cu credențialele."
                        : "Copil înscris în grupa " + group.getGroupName() + " folosind contul existent."
        );
    }

    // ── Anulare (admin) ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public void cancel(int entryId) {
        WaitlistEntry entry = waitlistRepository.findById(entryId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.WAITLIST_ENTRY_NOT_FOUND,
                        "Cererea cu id " + entryId + " nu a fost găsită."));

        if (entry.getStatus() == WaitlistStatus.ALLOCATED) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Nu se poate anula o cerere deja alocată.");
        }

        entry.setStatus(WaitlistStatus.CANCELLED);
        waitlistRepository.save(entry);

        log.info("WAITLIST_CANCEL entryId={} email={}", entryId, maskEmail(entry.getParentEmail()));
    }

    // ── Helpers private ───────────────────────────────────────────────────────

    /**
     * Mapare entitate → DTO răspuns pentru admin.
     */
    private WaitlistEntryResponse toResponse(WaitlistEntry e) {
        Integer allocatedGroupId   = e.getAllocatedGroup() != null ? e.getAllocatedGroup().getIdGroup()   : null;
        String  allocatedGroupName = e.getAllocatedGroup() != null ? e.getAllocatedGroup().getGroupName() : null;

        return new WaitlistEntryResponse(
                e.getId(),
                e.getParentFirstName(),
                e.getParentLastName(),
                e.getParentEmail(),
                e.getParentPhone(),
                e.getChildFirstName(),
                e.getChildLastName(),
                e.getChildAge(),
                e.getChildSchool(),
                e.getChildSchoolClass(),
                e.getPreferredCourseName(),
                e.getPreferredSchoolName(),
                e.getNotes(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getAllocatedAt(),
                allocatedGroupId,
                allocatedGroupName
        );
    }

    /**
     * Trimite emailul de notificare alocare asincron, în afara tranzacției.
     * Dacă mail server-ul e down → tranzacția BD nu se rollback-uiește.
     */
    @Async
    protected void sendAllocationEmailAsync(User parent, String rawPassword,
                                            String groupName, boolean isNewAccount) {
        try {
            emailService.sendWaitlistAllocated(parent, rawPassword, groupName, isNewAccount);
        } catch (Exception e) {
            log.warn("EMAIL_NOTIFICATION_FAILED event=waitlistAllocated parentId={} error=\"{}\"",
                    parent.getIdUser(), e.getMessage());
        }
    }

    /**
     * Mascare email pentru GDPR în loguri.
     * ion.popescu@gmail.com → ion***@***.com
     */
    private static String maskEmail(String email) {
        if (email == null) return "***";
        int atIdx = email.indexOf('@');
        if (atIdx < 0) return "***";
        String local       = email.substring(0, atIdx);
        String domain      = email.substring(atIdx + 1);
        String maskedLocal = local.substring(0, Math.min(3, local.length())) + "***";
        int lastDot        = domain.lastIndexOf('.');
        String tld         = (lastDot >= 0) ? domain.substring(lastDot) : domain;
        return maskedLocal + "@***" + tld;
    }
}
