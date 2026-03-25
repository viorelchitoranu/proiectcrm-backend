package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.*;
import com.springapp.proiectcrm.exception.BusinessException;
import com.springapp.proiectcrm.exception.ErrorCode;
import com.springapp.proiectcrm.model.*;
import com.springapp.proiectcrm.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TeacherServiceImpl implements TeacherService {

    private final UserRepository userRepository;
    private final GroupClassRepository groupClassRepository;
    private final ChildGroupRepository childGroupRepository;
    private final SessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Bucharest");
    private static final String TEACHER_ROLE_NAME = "TEACHER";
    private static final String ATTENDENCE_NOT_FOUND = "Attendance not found.";
    private static final String RECOVERY_REQUEST = "RECOVERY_REQUEST";
    private static final String CANCEL_REQUEST = "CANCEL_REQUEST";

    @Override
    public List<TeacherGroupSummaryResponse> getTeacherGroups(String authenticatedEmail) {
        User teacher = getAuthenticatedTeacher(authenticatedEmail);
        List<GroupClass> groups = groupClassRepository.findByTeacher(teacher);
        return groups.stream().map(this::mapToTeacherGroupSummary).toList();
    }

    @Override
    public List<TeacherGroupSummaryResponse> getActiveTeacherGroups(String authenticatedEmail) {
        User teacher = getAuthenticatedTeacher(authenticatedEmail);
        LocalDate today = LocalDate.now();

        return groupClassRepository.findByTeacher(teacher).stream()
                .filter(g -> Boolean.TRUE.equals(g.getIsActive()))
                .filter(g -> g.getGroupEndDate() == null || !today.isAfter(g.getGroupEndDate()))
                .map(this::mapToTeacherGroupSummary)
                .toList();
    }

    @Override
    public List<TeacherGroupSummaryResponse> getFinishedTeacherGroups(String authenticatedEmail) {
        User teacher = getAuthenticatedTeacher(authenticatedEmail);
        LocalDate today = LocalDate.now();

        return groupClassRepository.findByTeacher(teacher).stream()
                .filter(g -> g.getGroupEndDate() != null && today.isAfter(g.getGroupEndDate()))
                .map(this::mapToTeacherGroupSummary)
                .toList();
    }

    @Override
    public List<TeacherSessionSummaryResponse> getGroupSessions(String authenticatedEmail, int groupId) {
        User teacher = getAuthenticatedTeacher(authenticatedEmail);
        GroupClass group = requireGroupOwnedByTeacher(groupId, teacher);

        List<Session> sessions = sessionRepository.findByGroupOrderBySessionDateAsc(group);
        Integer maxSlots = group.getMaxRecoverySlots();

        List<TeacherSessionSummaryResponse> out = new ArrayList<>();
        for (Session s : sessions) {
            long used = 0;
            if (maxSlots != null && maxSlots > 0) {
                used = attendanceRepository.countRecoveryForSession(s);
            }

            out.add(new TeacherSessionSummaryResponse(
                    s.getIdSession(),
                    s.getSessionDate(),
                    s.getTime(),
                    s.getSessionStatus(),
                    s.getSchool() != null ? s.getSchool().getName()
                            : (group.getSchool() != null ? group.getSchool().getName() : null),
                    s.getSchool() != null ? s.getSchool().getAddress()
                            : (group.getSchool() != null ? group.getSchool().getAddress() : null),
                    s.getAttendanceTakenAt() != null,
                    used,
                    maxSlots
            ));
        }

        return out;
    }

    @Override
    public List<TeacherAttendanceRowResponse> getSessionAttendance(
            String authenticatedEmail,
            int groupId,
            int sessionId
    ) {
        User teacher = getAuthenticatedTeacher(authenticatedEmail);
        GroupClass group = requireGroupOwnedByTeacher(groupId, teacher);
        Session session = requireSessionInGroup(sessionId, group);

        List<ChildGroup> childGroups = childGroupRepository.findByGroupAndActiveTrue(group);
        List<Attendance> attendanceList = attendanceRepository.findBySession(session);

        Map<Integer, Attendance> byChildId = new HashMap<>();
        for (Attendance a : attendanceList) {
            if (a.getChild() != null) {
                byChildId.put(a.getChild().getIdChild(), a);
            }
        }

        return childGroups.stream()
                .map(cg -> {
                    Child child = cg.getChild();
                    Attendance a = byChildId.get(child.getIdChild());

                    User parent = child.getParent();
                    String parentName = parent != null
                            ? parent.getLastName() + " " + parent.getFirstName()
                            : null;

                    return new TeacherAttendanceRowResponse(
                            a != null ? a.getIdAttendance() : null,
                            child.getIdChild(),
                            child.getChildFirstName(),
                            child.getChildLastName(),
                            parentName,
                            parent != null ? parent.getPhone() : null,
                            parent != null ? parent.getEmail() : null,
                            a != null ? a.getStatus() : null,
                            a != null && a.isRecovery()
                    );
                })
                .toList();
    }

    @Override
    @Transactional
    public List<TeacherAttendanceRowResponse> updateSessionAttendance(
            String authenticatedEmail,
            int groupId,
            int sessionId,
            TeacherAttendanceUpdateRequest request
    ) {
        if (request == null || request.getRows() == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Request rows are required."
            );
        }

        User teacher = getAuthenticatedTeacher(authenticatedEmail);
        GroupClass group = requireGroupOwnedByTeacher(groupId, teacher);
        Session session = requireSessionInGroup(sessionId, group);

        ensureAttendanceAllowed(session);

        List<ChildGroup> childGroups = childGroupRepository.findByGroupAndActiveTrue(group);
        Map<Integer, Child> activeChildren = new HashMap<>();
        for (ChildGroup cg : childGroups) {
            if (cg.getChild() != null) {
                activeChildren.put(cg.getChild().getIdChild(), cg.getChild());
            }
        }

        LocalDateTime now = LocalDateTime.now();

        for (TeacherAttendanceUpdateRequest.Row row : request.getRows()) {
            Integer childId = row.getChildId();
            AttendanceStatus status = row.getStatus();

            if (childId == null) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_RULE_VIOLATION,
                        "childId este obligatoriu."
                );
            }

            if (status == null) {
                throw new BusinessException(
                        ErrorCode.BUSINESS_RULE_VIOLATION,
                        "status este obligatoriu pentru childId=" + childId
                );
            }

            Child child = activeChildren.get(childId);
            if (child == null) {
                throw new BusinessException(
                        ErrorCode.CHILD_NOT_IN_GROUP,
                        "Copilul cu id " + childId + " nu este activ în această grupă."
                );
            }

            Attendance attendance = attendanceRepository.findBySessionAndChild(session, child)
                    .orElseGet(() -> {
                        Attendance a = new Attendance();
                        a.setSession(session);
                        a.setChild(child);
                        a.setCreatedAt(now);
                        a.setRecovery(false);
                        return a;
                    });

            attendance.setStatus(status);
            attendance.setUpdatedAt(now);
            attendanceRepository.save(attendance);
        }

        session.setAttendanceTakenAt(now);
        sessionRepository.save(session);

        return getSessionAttendance(authenticatedEmail, groupId, sessionId);
    }

    @Override
    public List<TeacherParentRequestResponse> getParentRequests(
            String authenticatedEmail,
            AttendanceStatus type
    ) {
        User teacher = getAuthenticatedTeacher(authenticatedEmail);

        List<Attendance> all = attendanceRepository.findBySession_Group_Teacher(teacher);

        return all.stream()
                .filter(a -> !a.isRecovery())
                .map(a -> new AbstractMap.SimpleEntry<>(a, resolveRequestType(a)))
                .filter(e -> e.getValue() != null)
                .filter(e -> type == null || e.getValue() == type)
                .map(e -> mapToParentRequestResponse(e.getKey(), e.getValue()))
                .toList();
    }

    @Override
    @Transactional
    public void allocateRecovery(
            String authenticatedEmail,
            int attendanceId,
            TeacherAllocateRecoveryRequest request
    ) {
        // REFACTORIZARE (SonarCloud — Cognitive Complexity):
        // Metoda originală avea complexitate 30 (limita e 15).
        // Validările au fost extrase în 3 metode private:
        //   validateOriginalAttendance()  → validări pe attendance original
        //   validateTargetSession()       → validări pe sesiunea țintă
        //   bookRecovery()                → salvare attendance recovery + update original

        if (request == null || request.getTargetSessionId() <= 0) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "targetSessionId este obligatoriu."
            );
        }

        User teacher = getAuthenticatedTeacher(authenticatedEmail);

        Attendance original = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SESSION_NOT_FOUND,
                        ATTENDENCE_NOT_FOUND
                ));

        // Pasul 1: validări pe attendance original (sesiune, teacher, status, copil)
        GroupClass originalGroup = validateOriginalAttendance(original, teacher);
        Child child = original.getChild();

        // Pasul 2: idempotență — dacă e deja alocat pe aceeași sesiune țintă → return
        if (original.getStatus() == AttendanceStatus.RECOVERY_BOOKED) {
            if (original.getAssignedToSessionId() != null
                    && original.getAssignedToSessionId().equals(request.getTargetSessionId())) {
                return;
            }
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Recuperarea este deja alocată."
            );
        }

        // Pasul 3: validări pe sesiunea țintă (grupă, școală, sloturi, dată)
        Session targetSession = sessionRepository.findById(request.getTargetSessionId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SESSION_NOT_FOUND,
                        "Target session not found."
                ));

        validateTargetSession(targetSession, original.getSession(), originalGroup);

        // Pasul 4: verificare slot disponibil și salvare
        long used = attendanceRepository.countRecoveryForSession(targetSession);
        Integer targetMaxSlots = targetSession.getGroup().getMaxRecoverySlots();
        if (used >= targetMaxSlots) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Nu mai sunt locuri de recuperare disponibile pentru această sesiune."
            );
        }

        bookRecovery(original, targetSession, child);
    }

    /**
     * Validează attendance-ul original înainte de alocarea recuperării.
     *
     * Verifică:
     *   - Are sesiune și grupă valide
     *   - Aparține grupei profesorului autentificat
     *   - Status este RECOVERY_REQUESTED sau PENDING(RECOVERY_REQUEST)
     *   - Nu este el însuși un attendance de recovery
     *   - Are copil asociat și copilul este activ în grupă
     *
     * @return grupa originală (pentru validările ulterioare)
     */
    private GroupClass validateOriginalAttendance(Attendance original, User teacher) {
        Session originalSession = original.getSession();
        if (originalSession == null || originalSession.getGroup() == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Attendance-ul nu are grupă/sesiune valide."
            );
        }

        GroupClass originalGroup = originalSession.getGroup();
        if (originalGroup.getTeacher() == null
                || originalGroup.getTeacher().getIdUser() != teacher.getIdUser()) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Această cerere nu aparține profesorului autentificat."
            );
        }

        boolean isPendingRecovery = original.getStatus() == AttendanceStatus.PENDING
                && original.getNota() != null
                && original.getNota().trim().startsWith(RECOVERY_REQUEST);

        if (!(original.getStatus() == AttendanceStatus.RECOVERY_REQUESTED || isPendingRecovery)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Doar cererile RECOVERY_REQUESTED sau PENDING(RECOVERY_REQUEST) pot fi alocate."
            );
        }

        if (original.isRecovery()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Attendance-ul original nu poate fi unul de recovery."
            );
        }

        Child child = original.getChild();
        if (child == null) {
            throw new BusinessException(
                    ErrorCode.CHILD_NOT_FOUND,
                    "Attendance-ul nu are copil asociat."
            );
        }

        if (!childGroupRepository.existsByChildAndGroupAndActiveTrue(child, originalGroup)) {
            throw new BusinessException(
                    ErrorCode.CHILD_HAS_INACTIVE_ENROLLMENT,
                    "Copilul nu mai este activ în această grupă."
            );
        }

        return originalGroup;
    }

    /**
     * Validează sesiunea țintă pentru alocarea recuperării.
     *
     * Verifică:
     *   - Are grupă asociată
     *   - Aceeași școală ca sesiunea originală
     *   - Grupa țintă permite recuperări (maxRecoverySlots > 0)
     *   - Status PLANNED
     *   - Data în viitor
     *   - Nu e aceeași sesiune ca cea originală
     *
     * @param targetSession   sesiunea pe care se alocă recuperarea
     * @param originalSession sesiunea originală ratată
     * @param originalGroup   grupa originală (pentru verificarea școlii)
     */
    private void validateTargetSession(
            Session targetSession, Session originalSession, GroupClass originalGroup) {

        GroupClass targetGroup = targetSession.getGroup();
        if (targetGroup == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Target session nu are grupă asociată."
            );
        }

        School origSchool   = originalGroup.getSchool();
        School targetSchool = targetGroup.getSchool();
        if (origSchool == null || targetSchool == null
                || origSchool.getIdSchool() != targetSchool.getIdSchool()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Recuperarea se poate aloca doar în aceeași școală."
            );
        }

        Integer targetMaxSlots = targetGroup.getMaxRecoverySlots();
        if (targetMaxSlots == null || targetMaxSlots <= 0) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Grupa țintă nu permite recuperări."
            );
        }

        if (targetSession.getSessionStatus() != SessionStatus.PLANNED) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Poți aloca recuperare doar pe sesiuni PLANNED."
            );
        }

        if (targetSession.getSessionDate() == null
                || targetSession.getSessionDate().isBefore(LocalDate.now())) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Nu poți aloca recuperare pe sesiuni din trecut."
            );
        }

        if (targetSession.getIdSession() == originalSession.getIdSession()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Sesiunea țintă nu poate fi aceeași cu sesiunea originală."
            );
        }
    }

    /**
     * Salvează attendance-ul de recovery și actualizează cel original.
     *
     * Dacă există deja un attendance de recovery pentru această sesiune+copil
     * (creat anterior), îl refolosim și doar actualizăm originalul.
     * Altfel, creăm un attendance nou de recovery.
     *
     * @param original      attendance-ul original de actualizat
     * @param targetSession sesiunea pe care se face recuperarea
     * @param child         copilul pentru care se alocă recuperarea
     */
    private void bookRecovery(Attendance original, Session targetSession, Child child) {
        Session originalSession = original.getSession();
        LocalDateTime now = LocalDateTime.now();

        Optional<Attendance> existing = attendanceRepository.findBySessionAndChild(targetSession, child);
        if (existing.isPresent()) {
            Attendance ex = existing.get();
            if (ex.isRecovery()
                    && ex.getRecoveryForSessionId() != null
                    && ex.getRecoveryForSessionId().equals(originalSession.getIdSession())) {
                // Recovery deja creat pentru această sesiune — doar actualizăm originalul
                original.setAssignedToSessionId(targetSession.getIdSession());
                original.setStatus(AttendanceStatus.RECOVERY_BOOKED);
                original.setUpdatedAt(now);
                attendanceRepository.save(original);
                return;
            }
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Copilul are deja o prezență pentru această sesiune țintă."
            );
        }

        // Creare attendance nou de recovery
        Attendance recovery = new Attendance();
        recovery.setSession(targetSession);
        recovery.setChild(child);
        recovery.setStatus(AttendanceStatus.PENDING);
        recovery.setCreatedAt(now);
        recovery.setUpdatedAt(now);
        recovery.setRecovery(true);
        recovery.setRecoveryForSessionId(originalSession.getIdSession());
        recovery.setNota(original.getNota());
        attendanceRepository.save(recovery);

        // Actualizare attendance original
        original.setStatus(AttendanceStatus.RECOVERY_BOOKED);
        original.setAssignedToSessionId(targetSession.getIdSession());
        original.setUpdatedAt(now);
        attendanceRepository.save(original);
    }

    @Override
    @Transactional
    public StartGroupResponse startGroup(String authenticatedEmail, int groupId) {
        User teacher = getAuthenticatedTeacher(authenticatedEmail);
        GroupClass group = requireGroupOwnedByTeacher(groupId, teacher);

        if (group.getStartConfirmedAt() != null) {
            return new StartGroupResponse(
                    group.getIdGroup(),
                    group.getGroupName(),
                    group.getStartConfirmedAt(),
                    "Grupa a fost deja pornită la " + group.getStartConfirmedAt() + "."
            );
        }

        LocalDate today = LocalDate.now();
        LocalTime time = group.getSessionStartTime() != null ? group.getSessionStartTime() : LocalTime.MIDNIGHT;
        LocalDateTime startConfirmedAt = LocalDateTime.of(today, time);

        group.setStartConfirmedAt(startConfirmedAt);
        GroupClass saved = groupClassRepository.save(group);

        return new StartGroupResponse(
                saved.getIdGroup(),
                saved.getGroupName(),
                saved.getStartConfirmedAt(),
                "Grupa a fost pornită cu succes la " + saved.getStartConfirmedAt() + "."
        );
    }

    @Override
    @Transactional
    public void changeOwnPassword(String authenticatedEmail, TeacherChangeOwnPasswordRequest request) {
        if (request == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Request is required."
            );
        }

        User teacher = getAuthenticatedTeacher(authenticatedEmail);

        String oldPassword = request.getOldPassword();
        String newPassword = request.getNewPassword();

        if (oldPassword == null || oldPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Parola veche și parola nouă sunt obligatorii."
            );
        }

        if (newPassword.length() < 6) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Parola nouă trebuie să aibă minim 6 caractere."
            );
        }

        if (!passwordEncoder.matches(oldPassword, teacher.getPassword())) {
            throw new BusinessException(
                    ErrorCode.INVALID_OLD_PASSWORD,
                    "Parola veche nu este corectă."
            );
        }

        teacher.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(teacher);

        try {
            emailService.sendTeacherPasswordReset(teacher, newPassword);
        } catch (Exception ex) {
            System.err.println("Eroare la trimiterea email-ului de resetare parolă profesor: " + ex.getMessage());
        }
    }

    @Override
    public List<TeacherRecoveryTargetSessionResponse> getRecoveryTargetSessions(
            String authenticatedEmail,
            int attendanceId
    ) {
        // REFACTORIZARE (SonarCloud — Cognitive Complexity):
        // Metoda originală avea complexitate 31 (limita e 15).
        // Validările au fost extrase în validateRecoveryTargetRequest()
        // iar mapping-ul sesiunii în toRecoveryTargetResponse().

        User teacher = getAuthenticatedTeacher(authenticatedEmail);

        Attendance original = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SESSION_NOT_FOUND,
                        ATTENDENCE_NOT_FOUND
                ));

        // Validare și extragere școală
        School school = validateRecoveryTargetRequest(original, teacher);

        List<Session> targets = sessionRepository.findPlannedFutureRecoveryTargetsBySchool(
                school,
                LocalDate.now(),
                SessionStatus.PLANNED
        );

        // Filtrare și mapping — extrase în metodă privată
        return targets.stream()
                .filter(s -> isEligibleRecoveryTarget(s, original.getSession()))
                .map(s -> toRecoveryTargetResponse(s, attendanceRepository.countRecoveryForSession(s)))
                .toList();
    }

    /**
     * Validează că cererea de recovery este validă și returnează școala
     * pe care se vor căuta sesiunile țintă.
     *
     * Verifică:
     *   - Attendance are sesiune și grupă valide
     *   - Aparține grupei profesorului autentificat
     *   - Status este RECOVERY_REQUESTED sau PENDING(RECOVERY_REQUEST)
     *   - Poate determina școala cererii
     *
     * @return școala sesiunii originale (pentru căutarea sesiunilor țintă)
     */
    private School validateRecoveryTargetRequest(Attendance original, User teacher) {
        Session origSession = original.getSession();
        GroupClass origGroup = (origSession != null) ? origSession.getGroup() : null;

        if (origSession == null || origGroup == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Cerere invalidă."
            );
        }

        if (origGroup.getTeacher() == null
                || origGroup.getTeacher().getIdUser() != teacher.getIdUser()) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Nu ai drepturi pe această cerere."
            );
        }

        boolean isPendingRecovery = original.getStatus() == AttendanceStatus.PENDING
                && original.getNota() != null
                && original.getNota().trim().startsWith(RECOVERY_REQUEST);

        if (!(original.getStatus() == AttendanceStatus.RECOVERY_REQUESTED || isPendingRecovery)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Doar cererile RECOVERY_REQUESTED sau PENDING(RECOVERY_REQUEST) pot fi alocate."
            );
        }

        School school = (origSession.getSchool() != null)
                ? origSession.getSchool()
                : origGroup.getSchool();

        if (school == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Nu pot determina școala cererii."
            );
        }

        return school;
    }

    /**
     * Verifică dacă o sesiune este eligibilă ca țintă de recuperare.
     * Excludem: sesiunea originală, sesiuni fără grupă,
     * grupe fără sloturi de recuperare și sesiunile deja pline.
     */
    private boolean isEligibleRecoveryTarget(Session s, Session origSession) {
        if (s.getIdSession() == origSession.getIdSession()) return false;
        GroupClass g = s.getGroup();
        if (g == null) return false;
        Integer max = g.getMaxRecoverySlots();
        if (max == null || max <= 0) return false;
        long used = attendanceRepository.countRecoveryForSession(s);
        return used < max;
    }

    /**
     * Construiește un TeacherRecoveryTargetSessionResponse dintr-o sesiune eligibilă.
     * schoolName: din sesiune, cu fallback pe grupă.
     */
    private TeacherRecoveryTargetSessionResponse toRecoveryTargetResponse(Session s, long used) {
        GroupClass g = s.getGroup();
        User t = g.getTeacher();
        Integer max = g.getMaxRecoverySlots();

        String schoolName = s.getSchool() != null
                ? s.getSchool().getName()
                : (g.getSchool() != null ? g.getSchool().getName() : null);

        return new TeacherRecoveryTargetSessionResponse(
                s.getIdSession(),
                s.getSessionDate(),
                s.getTime(),
                g.getIdGroup(),
                g.getGroupName(),
                g.getCourse() != null ? g.getCourse().getName() : null,
                t != null ? t.getIdUser() : 0,
                t != null ? t.getLastName() + " " + t.getFirstName() : null,
                schoolName,
                used,
                max
        );
    }

    @Override
    @Transactional
    public void confirmCancelRequest(String authenticatedEmail, int attendanceId) {
        User teacher = getAuthenticatedTeacher(authenticatedEmail);

        Attendance a = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SESSION_NOT_FOUND,
                        ATTENDENCE_NOT_FOUND
                ));

        Session s = a.getSession();
        GroupClass g = (s != null) ? s.getGroup() : null;

        if (g == null || g.getTeacher() == null || g.getTeacher().getIdUser() != teacher.getIdUser()) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Cererea nu aparține profesorului."
            );
        }

        String note = a.getNota();
        boolean isPendingCancel =
                a.getStatus() == AttendanceStatus.PENDING
                        && note != null
                        && note.trim().startsWith(CANCEL_REQUEST);

        if (!(a.getStatus() == AttendanceStatus.CANCELLED_BY_PARENT || isPendingCancel)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Poți confirma doar cereri CANCELLED_BY_PARENT sau PENDING(CANCEL_REQUEST)."
            );
        }

        if (isPendingCancel) {
            String cleaned = note.trim()
                    .replaceFirst("^CANCEL_REQUEST\\s*\\|\\s*", "")
                    .replaceFirst("^CANCEL_REQUEST\\s*", "")
                    .trim();
            a.setNota(cleaned.isBlank() ? null : cleaned);
        }

        a.setStatus(AttendanceStatus.EXCUSED);
        a.setUpdatedAt(LocalDateTime.now());
        attendanceRepository.save(a);
    }

    private User getAuthenticatedTeacher(String authenticatedEmail) {
        User teacher = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.TEACHER_NOT_FOUND,
                        "Teacher not found."
                ));

        if (teacher.getRole() == null || teacher.getRole().getRoleName() == null) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "User-ul nu are rol setat."
            );
        }

        if (!TEACHER_ROLE_NAME.equalsIgnoreCase(teacher.getRole().getRoleName())) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "User-ul nu are rol TEACHER."
            );
        }

        return teacher;
    }

    private void ensureAttendanceAllowed(Session session) {
        if (session.getSessionDate() == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Sesiunea nu are dată setată."
            );
        }

        LocalDate today = LocalDate.now(APP_ZONE);

        if (session.getSessionDate().isAfter(today)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Poți lua prezența începând din ziua sesiunii."
            );
        }

        SessionStatus st = session.getSessionStatus();
        if (!(st == SessionStatus.PLANNED || st == SessionStatus.TAUGHT)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Nu poți lua prezența pentru sesiuni anulate/inactive."
            );
        }
    }

    private AttendanceStatus resolveRequestType(Attendance a) {
        if (a == null) return null;

        AttendanceStatus st = a.getStatus();
        String nota = (a.getNota() == null) ? "" : a.getNota().trim();

        if (st == AttendanceStatus.PENDING) {
            if (nota.startsWith(CANCEL_REQUEST)) return AttendanceStatus.CANCELLED_BY_PARENT;
            if (nota.startsWith(RECOVERY_REQUEST)) return AttendanceStatus.RECOVERY_REQUESTED;
            return null;
        }

        if (st == AttendanceStatus.CANCELLED_BY_PARENT) return AttendanceStatus.CANCELLED_BY_PARENT;
        if (st == AttendanceStatus.RECOVERY_REQUESTED) return AttendanceStatus.RECOVERY_REQUESTED;
        if (st == AttendanceStatus.RECOVERY_BOOKED) return AttendanceStatus.RECOVERY_REQUESTED;

        return null;
    }

    private GroupClass requireGroupOwnedByTeacher(int groupId, User teacher) {
        GroupClass group = groupClassRepository.findById(groupId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.GROUP_NOT_FOUND,
                        "Group not found."
                ));

        if (group.getTeacher() == null || group.getTeacher().getIdUser() != teacher.getIdUser()) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Această grupă nu aparține profesorului."
            );
        }

        return group;
    }

    private Session requireSessionInGroup(int sessionId, GroupClass group) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SESSION_NOT_FOUND,
                        "Session not found."
                ));

        if (session.getGroup() == null || session.getGroup().getIdGroup() != group.getIdGroup()) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Sesiunea nu aparține acestei grupe."
            );
        }

        return session;
    }

    private TeacherGroupSummaryResponse mapToTeacherGroupSummary(GroupClass g) {
        String schoolName = g.getSchool() != null ? g.getSchool().getName() : null;
        String schoolAddress = g.getSchool() != null ? g.getSchool().getAddress() : null;

        long totalSessions = sessionRepository.countByGroup(g);
        long heldSessions = sessionRepository.countByGroupAndSessionStatus(g, SessionStatus.TAUGHT);
        long cancelledSessions = sessionRepository.countByGroupAndSessionStatusIn(
                g,
                List.of(SessionStatus.CANCELED, SessionStatus.CANCELED_HOLIDAY, SessionStatus.CANCELED_MANUAL)
        );

        int progressPercent = totalSessions > 0
                ? (int) Math.round(heldSessions * 100.0 / totalSessions)
                : 0;

        long activeChildren = childGroupRepository.countByGroupAndActiveTrue(g);
        String status = computeGroupStatus(g);

        return new TeacherGroupSummaryResponse(
                g.getIdGroup(),
                g.getGroupName(),
                g.getCourse() != null ? g.getCourse().getName() : null,
                schoolName,
                schoolAddress,
                g.getGroupStartDate(),
                g.getGroupEndDate(),
                g.getSessionStartTime(),
                g.getIsActive(),
                (int) activeChildren,
                totalSessions,
                g.getGroupCreatedAt(),
                g.getStartConfirmedAt(),
                totalSessions,
                heldSessions,
                cancelledSessions,
                progressPercent,
                activeChildren,
                status
        );
    }

    private String computeGroupStatus(GroupClass g) {
        LocalDate today = LocalDate.now();

        LocalDate startRef = null;
        if (g.getStartConfirmedAt() != null) {
            startRef = g.getStartConfirmedAt().toLocalDate();
        } else if (g.getGroupStartDate() != null) {
            startRef = g.getGroupStartDate();
        }

        LocalDate end = g.getGroupEndDate();

        if (end != null && today.isAfter(end)) return "FINISHED";
        if (startRef == null || today.isBefore(startRef)) return "NOT_STARTED";
        return "ONGOING";
    }

    /**
     * Mapează un Attendance + tipul cererii la TeacherParentRequestResponse.
     *
     * REFACTORIZARE (SonarCloud — Cognitive Complexity):
     *   Metoda originală avea complexitate 18 (limita e 15) din cauza
     *   a 15+ expresii ternare nested direct în constructorul DTO-ului.
     *   Fix: extracem câmpurile în variabile locale cu nume descriptive
     *   înainte de apelul constructorului — fiecare variabilă rezolvă
     *   o singură expresie ternară, reducând complexitatea la ~5.
     *
     * Toate câmpurile folosesc valori default sigure când entitatea lipsește:
     *   - int/Integer → 0 (ex: childId, sessionId, groupId)
     *   - String      → null (ex: parentName, groupName)
     *   - Object      → null (ex: sessionDate, sessionTime)
     *
     * @param a    attendance-ul de mapat (cererea părintelui)
     * @param type tipul cererii determinat din status + nota (CANCEL/RECOVERY)
     */
    /**
     * Mapează un Attendance + tipul cererii la TeacherParentRequestResponse.
     *
     * REFACTORIZARE (SonarCloud — Cognitive Complexity):
     *   Metoda avea complexitate 18 (limita e 15) chiar după extragerea în variabile locale.
     *   Cauza: expresiile ternare cu condiție dublă (&&) contribuie câte 2 la complexitate:
     *     `g != null && g.getCourse() != null ? ...` = 2 (nu 1)
     *     `g != null && g.getSchool() != null  ? ...` = 2 (nu 1)
     *   Fix: cele 2 expresii compound extrase în metode helper private
     *   (resolveCourseName, resolveSchoolName) → complexitate redusă la ~14.
     *
     * Toate câmpurile folosesc valori default sigure când entitatea lipsește:
     *   - int/Integer → 0 (ex: childId, sessionId, groupId)
     *   - String      → null (ex: parentName, groupName)
     *   - Object      → null (ex: sessionDate, sessionTime)
     *
     * @param a    attendance-ul de mapat (cererea părintelui)
     * @param type tipul cererii determinat din status + nota (CANCEL/RECOVERY)
     */
    private TeacherParentRequestResponse mapToParentRequestResponse(
            Attendance a, AttendanceStatus type) {

        // ── Extragere entități asociate ───────────────────────────────────────
        Session    s      = a.getSession();
        GroupClass g      = s != null ? s.getGroup() : null;
        Child      child  = a.getChild();
        User       parent = child != null ? child.getParent() : null;

        // ── Câmpuri copil ─────────────────────────────────────────────────────
        int    childId   = child != null ? child.getIdChild() : 0;
        String childName = child != null
                ? child.getChildLastName() + " " + child.getChildFirstName()
                : null;

        // ── Câmpuri părinte ───────────────────────────────────────────────────
        String parentName  = parent != null
                ? parent.getLastName() + " " + parent.getFirstName()
                : null;
        String parentPhone = parent != null ? parent.getPhone() : null;
        String parentEmail = parent != null ? parent.getEmail() : null;

        // ── Câmpuri sesiune ───────────────────────────────────────────────────
        int        sessionId   = s != null ? s.getIdSession() : 0;
        LocalDate  sessionDate = s != null ? s.getSessionDate() : null;
        LocalTime  sessionTime = s != null ? s.getTime() : null;

        // ── Câmpuri grupă ─────────────────────────────────────────────────────
        int    groupId    = g != null ? g.getIdGroup() : 0;
        String groupName  = g != null ? g.getGroupName() : null;
        // Extrase în metode helper — condiția && dublu contribuia 2 la complexitate
        String courseName = resolveCourseName(g);
        String schoolName = resolveSchoolName(g);

        // ── Câmpuri tip cerere / status ───────────────────────────────────────
        // typeName: CANCEL_REQUEST sau RECOVERY_REQUEST — determinat de resolveRequestType()
        String typeName   = type != null ? type.name() : null;
        // statusName: starea curentă a attendance-ului (PENDING, EXCUSED etc.)
        String statusName = a.getStatus() != null ? a.getStatus().name() : null;

        return new TeacherParentRequestResponse(
                a.getIdAttendance(),
                childId,
                childName,
                parentName,
                parentPhone,
                parentEmail,
                sessionId,
                sessionDate,
                sessionTime,
                groupName,
                courseName,
                schoolName,
                typeName,
                a.getNota(),
                groupId,
                a.getAssignedToSessionId(),
                statusName
        );
    }

    /**
     * Returnează numele cursului grupei sau null dacă lipsește.
     * Extrasă din mapToParentRequestResponse pentru a reduce complexitatea ciclomatică —
     * condiția `g != null && g.getCourse() != null` contribuia 2 puncte de complexitate.
     *
     * @param g grupa (poate fi null)
     * @return  numele cursului sau null
     */
    private String resolveCourseName(GroupClass g) {
        return g != null && g.getCourse() != null ? g.getCourse().getName() : null;
    }

    /**
     * Returnează numele școlii grupei sau null dacă lipsește.
     * Extrasă din mapToParentRequestResponse pentru a reduce complexitatea ciclomatică —
     * condiția `g != null && g.getSchool() != null` contribuia 2 puncte de complexitate.
     *
     * @param g grupa (poate fi null)
     * @return  numele școlii sau null
     */
    private String resolveSchoolName(GroupClass g) {
        return g != null && g.getSchool() != null ? g.getSchool().getName() : null;
    }
}