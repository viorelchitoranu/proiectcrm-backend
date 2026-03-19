package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.*;
import com.springapp.proiectcrm.exception.BusinessException;
import com.springapp.proiectcrm.exception.ErrorCode;
import com.springapp.proiectcrm.model.*;
import com.springapp.proiectcrm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implementare a serviciului de acțiuni disponibile unui PĂRINTE autentificat.
 *
 * Responsabilități principale:
 *   - Vizualizare copii proprii și înscrierile acestora
 *   - Consultare program sesiuni (cu stare prezență și flag-uri de acțiune)
 *   - Trimitere cerere de anulare sesiune (CANCEL_REQUEST)
 *   - Trimitere cerere de recuperare sesiune (RECOVERY_REQUEST)
 *   - Schimbare parolă proprie
 *
 * Reguli de business critice pentru cereri de sesiune:
 *   - Un părinte poate trimite MAXIM O cerere de anulare per sesiune per copil.
 *     Dacă există deja o cerere PENDING cu nota "CANCEL_REQUEST", o nouă cerere
 *     este respinsă cu CANCEL_ALREADY_REQUESTED.
 *   - Un părinte poate trimite MAXIM O cerere de recuperare per sesiune per copil.
 *     Dacă există deja o cerere PENDING cu nota "RECOVERY_REQUEST" sau sesiunea
 *     are deja status RECOVERY_BOOKED, cererea este respinsă cu RECOVERY_ALREADY_REQUESTED.
 *
 * Flag-uri de acțiune din getChildGroupSchedule():
 *   cancellable            = sesiune PLANNED + >24h + fără cerere procesată (EXCUSED/RECOVERY_BOOKED) și fără CANCEL_REQUEST PENDING
 *   recoveryRequestAllowed = sesiune PLANNED + >24h + fără cerere procesată (EXCUSED/RECOVERY_BOOKED) și fără RECOVERY_REQUEST PENDING/BOOKED
 *   Aceste flag-uri controlează butoanele din UI — dacă sunt false, butoanele sunt disabled.
 */
@Service
@RequiredArgsConstructor
public class ParentServiceImpl implements ParentService {

    private final UserRepository          userRepository;
    private final ChildRepository         childRepository;
    private final ChildGroupRepository    childGroupRepository;
    private final GroupClassRepository    groupClassRepository;
    private final SessionRepository       sessionRepository;
    private final AttendanceRepository    attendanceRepository;
    private final PasswordEncoder         passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    private static final String PARENT_ROLE_NAME = "PARENT";

    // Prefix-uri folosite în câmpul `nota` al Attendance pentru a identifica tipul cererii.
    // ATENTIE: aceste constante trebuie să fie identice cu ce verificăm în guards și în
    // calculul flag-urilor din getChildGroupSchedule(). Orice schimbare → actualizați TOATE locurile.
    private static final String NOTA_CANCEL_PREFIX   = "CANCEL_REQUEST";
    private static final String NOTA_RECOVERY_PREFIX = "RECOVERY_REQUEST";

    // ── Vizualizare copii ─────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ChildSummaryResponse> getChildren(String authenticatedEmail) {
        User parent = getAuthenticatedParent(authenticatedEmail);
        List<Child> children = childRepository.findByParent(parent);

        return children.stream()
                .map(c -> new ChildSummaryResponse(
                        c.getIdChild(),
                        c.getChildFirstName(),
                        c.getChildLastName(),
                        c.getAge(),
                        c.getSchool(),
                        c.getSchoolClass()
                ))
                .toList();
    }

    // ── Vizualizare înscrieri ─────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ChildEnrollmentResponse> getChildEnrollments(String authenticatedEmail, int childId) {
        User parent = getAuthenticatedParent(authenticatedEmail);
        Child child = getOwnedChild(parent, childId);

        List<ChildGroup> enrollments = childGroupRepository.findByChildAndActiveTrue(child);

        return enrollments.stream()
                .map(cg -> {
                    GroupClass group = cg.getGroup();
                    String status = computeGroupStatus(group);
                    return new ChildEnrollmentResponse(
                            group.getIdGroup(),
                            group.getCourse()  != null ? group.getCourse().getName()   : null,
                            group.getGroupName(),
                            group.getSchool()  != null ? group.getSchool().getName()   : null,
                            group.getSchool()  != null ? group.getSchool().getAddress(): null,
                            group.getGroupStartDate(),
                            group.getGroupEndDate(),
                            group.getSessionStartTime(),
                            status
                    );
                })
                .toList();
    }

    // ── Program sesiuni (cu flag-uri de acțiune) ──────────────────────────────

    /**
     * Returnează programul complet al unui copil într-o grupă, inclusiv
     * flag-urile care controlează butoanele din UI.
     *
     * Flag-uri calculate per sesiune:
     *   cancellable            — butonul "Anulează" este activ
     *   recoveryRequestAllowed — butonul "Cere recuperare" este activ
     *
     * Regula finală: dacă profesorul a confirmat ORICE cerere pe sesiunea respectivă
     * (status = EXCUSED sau RECOVERY_BOOKED), AMBELE butoane se dezactivează.
     * Un părinte nu poate face o a doua cerere după ce profesorul a procesat prima.
     *
     * Ambele flag-uri iau în calcul NU DOAR condițiile de timp (>24h, PLANNED),
     * ci și cererile deja existente pentru sesiunea respectivă.
     * Astfel UI-ul reflectă întotdeauna starea reală, fără să mai depindă
     * de validarea backend ca primă linie de apărare.
     */
    @Override
    @Transactional(readOnly = true)
    public ChildGroupScheduleResponse getChildGroupSchedule(
            String authenticatedEmail, int childId, int groupId) {

        User parent = getAuthenticatedParent(authenticatedEmail);
        Child child = getOwnedChild(parent, childId);

        GroupClass group = groupClassRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.GROUP_NOT_FOUND, "Group not found"));

        ChildGroup childGroup = childGroupRepository.findByChildAndGroup(child, group)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CHILD_NOT_IN_GROUP, "Copilul nu este înscris în această grupă."));

        if (Boolean.FALSE.equals(childGroup.getActive())) {
            throw new BusinessException(
                    ErrorCode.CHILD_HAS_INACTIVE_ENROLLMENT,
                    "Copilul nu mai este activ în această grupă.");
        }

        List<Session> sessions = sessionRepository.findByGroupOrderBySessionDateAsc(group);

        // Data de la care sesiunile sunt vizibile părintelui — din momentul confirmării startului
        LocalDate visibleFromDate = null;
        if (group.getStartConfirmedAt() != null) {
            visibleFromDate = group.getStartConfirmedAt().toLocalDate();
        } else if (group.getGroupStartDate() != null) {
            visibleFromDate = group.getGroupStartDate();
        }

        final LocalDate  filterFrom = visibleFromDate;
        final LocalDateTime now     = LocalDateTime.now();

        // Preload attendance — un singur query pentru toate sesiunile grupei, anti N+1
        Map<Integer, Attendance> attendanceBySessionId = new HashMap<>();
        for (Session s : sessions) {
            attendanceRepository.findBySessionAndChild(s, child).ifPresent(a -> {
                if (a.getSession() != null) {
                    attendanceBySessionId.put(a.getSession().getIdSession(), a);
                }
            });
        }

        List<SessionSummaryResponse> sessionSummaries = sessions.stream()
                .filter(s -> {
                    // Filtrăm sesiunile anterioare datei de start confirmate
                    if (filterFrom == null || s.getSessionDate() == null) return true;
                    return !s.getSessionDate().isBefore(filterFrom);
                })
                .map(s -> {
                    String schoolName    = s.getSchool() != null ? s.getSchool().getName()    : (group.getSchool() != null ? group.getSchool().getName()    : null);
                    String schoolAddress = s.getSchool() != null ? s.getSchool().getAddress() : (group.getSchool() != null ? group.getSchool().getAddress() : null);

                    // Calculăm datetime-ul sesiunii pentru verificarea celor 24h
                    LocalDateTime sessionDateTime = null;
                    if (s.getSessionDate() != null) {
                        LocalTime startTime = (s.getTime() != null) ? s.getTime() : LocalTime.MIDNIGHT;
                        sessionDateTime = LocalDateTime.of(s.getSessionDate(), startTime);
                    }

                    boolean isPlanned      = s.getSessionStatus() == SessionStatus.PLANNED;
                    boolean atLeast24hBefore = sessionDateTime != null
                            && !sessionDateTime.isBefore(now.plusHours(24));

                    // ── Calcul flag-uri de acțiune ────────────────────────────
                    // Condiția de bază: sesiune PLANNED și cel puțin 24h distanță
                    boolean baseCondition = isPlanned && atLeast24hBefore;

                    Attendance a = attendanceBySessionId.get(s.getIdSession());

                    // PROCESATĂ: profesorul a confirmat deja O cerere pe această sesiune?
                    // EXCUSED       = profesorul a aprobat cererea de anulare
                    // RECOVERY_BOOKED = profesorul a alocat sesiunea de recuperare
                    // În ambele cazuri, sesiunea este considerată "rezolvată" →
                    // AMBELE butoane se dezactivează, indiferent de tipul cererii confirmate.
                    boolean alreadyProcessed = a != null
                            && (a.getStatus() == AttendanceStatus.EXCUSED
                            || a.getStatus() == AttendanceStatus.RECOVERY_BOOKED);

                    // CANCEL PENDING: există cerere de anulare în așteptare?
                    // Dacă da → butonul "Anulează" rămâne disabled în UI
                    boolean alreadyCancelPending = a != null
                            && a.getStatus() == AttendanceStatus.PENDING
                            && a.getNota() != null
                            && a.getNota().startsWith(NOTA_CANCEL_PREFIX);

                    // RECOVERY PENDING: există cerere de recuperare în așteptare?
                    // Dacă da → butonul "Cere recuperare" rămâne disabled în UI
                    boolean alreadyRecoveryPending = a != null
                            && a.getStatus() == AttendanceStatus.PENDING
                            && a.getNota() != null
                            && a.getNota().startsWith(NOTA_RECOVERY_PREFIX);

                    // Flag-urile finale:
                    //   - alreadyProcessed blochează AMBELE butoane simultan
                    //   - alreadyCancelPending   blochează doar butonul de anulare
                    //   - alreadyRecoveryPending blochează doar butonul de recuperare
                    boolean cancellable            = baseCondition && !alreadyProcessed && !alreadyCancelPending;
                    boolean recoveryRequestAllowed = baseCondition && !alreadyProcessed && !alreadyRecoveryPending;

                    // ── Date prezență ──────────────────────────────────────────
                    AttendanceStatus childAttendanceStatus = (a != null ? a.getStatus()              : null);
                    Integer assignedToSessionId            = (a != null ? a.getAssignedToSessionId() : null);
                    boolean isRecoveryAttendance           = (a != null && a.isRecovery());

                    return new SessionSummaryResponse(
                            s.getIdSession(),
                            s.getSessionDate(),
                            s.getTime(),
                            s.getSessionStatus(),
                            schoolName,
                            schoolAddress,
                            cancellable,
                            recoveryRequestAllowed,
                            (childAttendanceStatus != null ? childAttendanceStatus.name() : null),
                            assignedToSessionId,
                            isRecoveryAttendance
                    );
                })
                .toList();

        return new ChildGroupScheduleResponse(
                child.getIdChild(),
                child.getChildFirstName(),
                child.getChildLastName(),
                group.getIdGroup(),
                group.getGroupName(),
                group.getCourse() != null ? group.getCourse().getName()    : null,
                group.getSchool() != null ? group.getSchool().getName()    : null,
                group.getSchool() != null ? group.getSchool().getAddress() : null,
                group.getGroupStartDate(),
                group.getGroupEndDate(),
                group.getSessionStartTime(),
                computeGroupStatus(group),
                sessionSummaries
        );
    }

    // ── Cerere anulare sesiune ────────────────────────────────────────────────

    /**
     * Trimite o cerere de anulare a unei sesiuni pentru un copil.
     *
     * Regulă de unicitate: un părinte poate trimite O SINGURĂ cerere de anulare
     * per sesiune per copil. O a doua tentativă este respinsă cu CANCEL_ALREADY_REQUESTED.
     *
     * Starea attendance după trimitere: PENDING cu nota "CANCEL_REQUEST | motiv"
     * Profesorul vede cererea în panoul său și o poate aproba sau respinge.
     */
    @Override
    @Transactional
    public ParentSessionActionResponse cancelChildSession(
            String authenticatedEmail,
            int childId,
            int sessionId,
            ParentSessionActionRequest request
    ) {
        User parent = getAuthenticatedParent(authenticatedEmail);
        Child child = getOwnedChild(parent, childId);

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SESSION_NOT_FOUND, "Session not found"));

        GroupClass group = session.getGroup();
        ChildGroup childGroup = childGroupRepository.findByChildAndGroup(child, group)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CHILD_NOT_IN_GROUP, "Copilul nu este înscris în această grupă."));

        if (Boolean.FALSE.equals(childGroup.getActive())) {
            throw new BusinessException(
                    ErrorCode.CHILD_HAS_INACTIVE_ENROLLMENT,
                    "Copilul nu mai este activ în această grupă.");
        }

        ensureCancellableOrRecoveryAllowed(session);

        // Căutăm attendance-ul existent ÎNAINTE de a decide ce facem
        Optional<Attendance> existingOpt = attendanceRepository.findBySessionAndChild(session, child);

        if (existingOpt.isPresent()) {
            Attendance existing = existingOpt.get();

            // Guard: prezentă deja marcată → nu are sens să anulezi
            if (existing.getStatus() == AttendanceStatus.PRESENT) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Nu poți anula o sesiune deja marcată ca prezentă.");
            }

            // OBLIGATORIU: guard unicitate cerere anulare
            // Dacă există deja un CANCEL_REQUEST PENDING, respingem noua cerere.
            // Fără acest guard, un părinte poate trimite cereri repetate suprascriind nota.
            if (existing.getStatus() == AttendanceStatus.PENDING
                    && existing.getNota() != null
                    && existing.getNota().startsWith(NOTA_CANCEL_PREFIX)) {
                throw new BusinessException(
                        ErrorCode.CANCEL_ALREADY_REQUESTED,
                        "Ai trimis deja o cerere de anulare pentru această sesiune. "
                                + "Aceasta este în așteptare (PENDING) — profesorul o va procesa în curând.");
            }
        }

        // Dacă nu există attendance → creăm unul nou; dacă există → îl actualizăm
        Attendance attendance = existingOpt.orElseGet(() -> {
            Attendance a = new Attendance();
            a.setSession(session);
            a.setChild(child);
            a.setCreatedAt(LocalDateTime.now());
            a.setRecovery(false);
            return a;
        });

        // Setăm PENDING + nota structurată pentru identificare ulterioară
        attendance.setStatus(AttendanceStatus.PENDING);
        attendance.setUpdatedAt(LocalDateTime.now());

        String reason = (request != null) ? request.getReason() : null;
        attendance.setNota((reason == null || reason.isBlank())
                ? NOTA_CANCEL_PREFIX
                : (NOTA_CANCEL_PREFIX + " | " + reason.trim()));

        attendanceRepository.save(attendance);

        return new ParentSessionActionResponse(
                child.getIdChild(),
                session.getIdSession(),
                "CANCEL",
                "Cererea de anulare a fost trimisă și este în așteptare (PENDING).");
    }

    // ── Cerere recuperare sesiune ─────────────────────────────────────────────

    /**
     * Trimite o cerere de recuperare a unei sesiuni pentru un copil.
     *
     * Regulă de unicitate: un părinte poate trimite O SINGURĂ cerere de recuperare
     * per sesiune per copil. O a doua tentativă este respinsă cu RECOVERY_ALREADY_REQUESTED.
     * De asemenea, dacă profesorul a alocat deja recuperarea (RECOVERY_BOOKED), cererea e respinsă.
     *
     * Starea attendance după trimitere: PENDING cu nota "RECOVERY_REQUEST | motiv"
     * Profesorul vede cererea în panoul său și alocă manual sesiunea de recuperare.
     */
    @Override
    @Transactional
    public ParentSessionActionResponse requestRecoveryForChildSession(
            String authenticatedEmail,
            int childId,
            int sessionId,
            ParentSessionActionRequest request
    ) {
        User parent = getAuthenticatedParent(authenticatedEmail);
        Child child = getOwnedChild(parent, childId);

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SESSION_NOT_FOUND, "Session not found"));

        GroupClass group = session.getGroup();
        ChildGroup childGroup = childGroupRepository.findByChildAndGroup(child, group)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CHILD_NOT_IN_GROUP, "Copilul nu este înscris în această grupă."));

        if (Boolean.FALSE.equals(childGroup.getActive())) {
            throw new BusinessException(
                    ErrorCode.CHILD_HAS_INACTIVE_ENROLLMENT,
                    "Copilul nu mai este activ în această grupă.");
        }

        ensureCancellableOrRecoveryAllowed(session);

        // Căutăm attendance-ul existent ÎNAINTE de a decide ce facem
        Optional<Attendance> existingOpt = attendanceRepository.findBySessionAndChild(session, child);

        if (existingOpt.isPresent()) {
            Attendance existing = existingOpt.get();

            // Guard: prezentă deja marcată → nu are sens să ceri recuperare
            if (existing.getStatus() == AttendanceStatus.PRESENT) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Nu poți cere recuperare pentru o sesiune deja marcată prezentă.");
            }

            // Guard: recuperarea deja alocată de profesor → nu mai e nevoie de cerere nouă
            if (existing.getStatus() == AttendanceStatus.RECOVERY_BOOKED) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Recuperarea este deja alocată pentru această sesiune.");
            }

            // OBLIGATORIU: guard unicitate cerere recuperare
            // Dacă există deja un RECOVERY_REQUEST PENDING, respingem noua cerere.
            // Fără acest guard, un părinte poate trimite cereri repetate suprascriind nota.
            if (existing.getStatus() == AttendanceStatus.PENDING
                    && existing.getNota() != null
                    && existing.getNota().startsWith(NOTA_RECOVERY_PREFIX)) {
                throw new BusinessException(
                        ErrorCode.RECOVERY_ALREADY_REQUESTED,
                        "Ai trimis deja o cerere de recuperare pentru această sesiune. "
                                + "Aceasta este în așteptare (PENDING) — profesorul o va procesa în curând.");
            }
        }

        // Dacă nu există attendance → creăm unul nou; dacă există → îl actualizăm
        Attendance attendance = existingOpt.orElseGet(() -> {
            Attendance a = new Attendance();
            a.setSession(session);
            a.setChild(child);
            a.setCreatedAt(LocalDateTime.now());
            a.setRecovery(false);
            return a;
        });

        // Setăm PENDING + nota structurată pentru identificare ulterioară
        attendance.setStatus(AttendanceStatus.PENDING);
        attendance.setUpdatedAt(LocalDateTime.now());

        String reason = (request != null) ? request.getReason() : null;
        attendance.setNota((reason == null || reason.isBlank())
                ? NOTA_RECOVERY_PREFIX
                : (NOTA_RECOVERY_PREFIX + " | " + reason.trim()));

        attendanceRepository.save(attendance);

        return new ParentSessionActionResponse(
                child.getIdChild(),
                session.getIdSession(),
                "RECOVERY_REQUEST",
                "Cererea de recuperare a fost trimisă și este în așteptare (PENDING). "
                        + "Profesorul va aloca o sesiune de recuperare.");
    }

    // ── Schimbare parolă ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public ParentPasswordUpdateResponse updateParentPassword(
            String authenticatedEmail,
            ParentPasswordUpdateRequest request
    ) {
        User parent = getAuthenticatedParent(authenticatedEmail);

        String rawNewPassword = request.getNewPassword();
        parent.setPassword(passwordEncoder.encode(rawNewPassword));
        User saved = userRepository.save(parent);

        // Trimitem email de confirmare după schimbarea parolei (async, după commit)
        eventPublisher.publishEvent(new ParentPasswordResetEvent(
                saved.getEmail(),
                saved.getFirstName(),
                saved.getLastName(),
                rawNewPassword
        ));

        return mapParent(saved);
    }

    // ── Helpers private ───────────────────────────────────────────────────────

    /**
     * Găsește un User după email și verifică că are rol PARENT.
     * Aruncă BusinessException dacă user-ul nu există sau are alt rol.
     */
    private User getAuthenticatedParent(String authenticatedEmail) {
        User parent = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PARENT_NOT_FOUND, "Parent not found"));

        if (parent.getRole() == null || parent.getRole().getRoleName() == null) {
            throw new BusinessException(
                    ErrorCode.USER_NOT_PARENT_ROLE, "User-ul nu are rol setat.");
        }
        if (!PARENT_ROLE_NAME.equalsIgnoreCase(parent.getRole().getRoleName())) {
            throw new BusinessException(
                    ErrorCode.USER_NOT_PARENT_ROLE, "User-ul nu are rol parent.");
        }

        return parent;
    }

    /**
     * Găsește un copil după ID și verifică că aparține părintelui autentificat.
     * Previne accesul la datele copiilor altor părinți.
     */
    private Child getOwnedChild(User parent, int childId) {
        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.CHILD_NOT_FOUND, "Child not found"));

        // ATENTIE: verificare ownership — un parinte nu poate actiona pe copiii altui parinte
        if (child.getParent() == null || child.getParent().getIdUser() != parent.getIdUser()) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED, "Copilul nu aparține acestui părinte.");
        }

        return child;
    }

    /**
     * Calculează statusul textual al grupei față de data curentă.
     * Folosit în răspunsurile trimise frontend-ului pentru afișare.
     */
    private String computeGroupStatus(GroupClass group) {
        LocalDate today = LocalDate.now();

        LocalDate startRef = null;
        if (group.getStartConfirmedAt() != null) {
            startRef = group.getStartConfirmedAt().toLocalDate();
        } else if (group.getGroupStartDate() != null) {
            startRef = group.getGroupStartDate();
        }

        LocalDate end = group.getGroupEndDate();

        if (end != null && today.isAfter(end))           return "FINISHED";
        if (startRef == null || today.isBefore(startRef)) return "NOT_STARTED";
        return "ONGOING";
    }

    /**
     * Validează că o sesiune poate fi anulată sau că se poate cere recuperare.
     * Condiții:
     *   1. Sesiunea trebuie să fie PLANNED (nu CANCELLED, FINISHED etc.)
     *   2. Sesiunea trebuie să fie cel puțin 24h în viitor
     *
     * Această metodă validează condițiile de TIMP și STATUS ale sesiunii.
     * Unicitatea cererilor (un singur CANCEL/RECOVERY per sesiune) este validată
     * separat în cancelChildSession() și requestRecoveryForChildSession().
     */
    private void ensureCancellableOrRecoveryAllowed(Session s) {
        if (s.getSessionStatus() != SessionStatus.PLANNED) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Acțiunea este permisă doar pentru sesiuni PLANNED.");
        }

        if (s.getSessionDate() == null) return;

        LocalTime t = (s.getTime() != null) ? s.getTime() : LocalTime.MIDNIGHT;
        LocalDateTime sessionDt = LocalDateTime.of(s.getSessionDate(), t);

        // Limita de 24h: protejează profesorul de anulări de ultim moment
        if (sessionDt.isBefore(LocalDateTime.now().plusHours(24))) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Acțiunea este permisă doar cu cel puțin 24h înainte de sesiune.");
        }
    }

    private ParentPasswordUpdateResponse mapParent(User parent) {
        Boolean active = parent.getActive();
        if (active == null) active = Boolean.TRUE;  // compatibilitate cu date vechi fără coloana active

        return new ParentPasswordUpdateResponse(
                parent.getIdUser(),
                parent.getFirstName(),
                parent.getLastName(),
                parent.getEmail(),
                parent.getPhone(),
                active,
                parent.getCreatedAt()
        );
    }
}
