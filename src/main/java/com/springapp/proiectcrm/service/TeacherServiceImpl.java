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
                        "Attendance not found."
                ));

        Session originalSession = original.getSession();
        if (originalSession == null || originalSession.getGroup() == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Attendance-ul nu are grupă/sesiune valide."
            );
        }

        GroupClass originalGroup = originalSession.getGroup();

        if (originalGroup.getTeacher() == null || originalGroup.getTeacher().getIdUser() != teacher.getIdUser()) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Această cerere nu aparține profesorului autentificat."
            );
        }

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

        boolean isPendingRecovery =
                original.getStatus() == AttendanceStatus.PENDING
                        && original.getNota() != null
                        && original.getNota().trim().startsWith("RECOVERY_REQUEST");

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

        Session targetSession = sessionRepository.findById(request.getTargetSessionId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SESSION_NOT_FOUND,
                        "Target session not found."
                ));

        GroupClass targetGroup = targetSession.getGroup();
        if (targetGroup == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Target session nu are grupă asociată."
            );
        }

        School origSchool = originalGroup.getSchool();
        School targetSchool = targetGroup.getSchool();
        if (origSchool == null || targetSchool == null || origSchool.getIdSchool() != targetSchool.getIdSchool()) {
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

        if (targetSession.getSessionDate() == null || targetSession.getSessionDate().isBefore(LocalDate.now())) {
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

        long used = attendanceRepository.countRecoveryForSession(targetSession);
        if (used >= targetMaxSlots) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Nu mai sunt locuri de recuperare disponibile pentru această sesiune."
            );
        }

        Optional<Attendance> existing = attendanceRepository.findBySessionAndChild(targetSession, child);
        if (existing.isPresent()) {
            Attendance ex = existing.get();

            if (ex.isRecovery()
                    && ex.getRecoveryForSessionId() != null
                    && ex.getRecoveryForSessionId().equals(originalSession.getIdSession())) {
                LocalDateTime now = LocalDateTime.now();
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

        LocalDateTime now = LocalDateTime.now();

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
        User teacher = getAuthenticatedTeacher(authenticatedEmail);

        Attendance original = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SESSION_NOT_FOUND,
                        "Attendance not found."
                ));

        Session origSession = original.getSession();
        GroupClass origGroup = (origSession != null) ? origSession.getGroup() : null;

        if (origSession == null || origGroup == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Cerere invalidă."
            );
        }

        if (origGroup.getTeacher() == null || origGroup.getTeacher().getIdUser() != teacher.getIdUser()) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Nu ai drepturi pe această cerere."
            );
        }

        boolean isPendingRecovery =
                original.getStatus() == AttendanceStatus.PENDING
                        && original.getNota() != null
                        && original.getNota().trim().startsWith("RECOVERY_REQUEST");

        if (!(original.getStatus() == AttendanceStatus.RECOVERY_REQUESTED || isPendingRecovery)) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Doar cererile RECOVERY_REQUESTED sau PENDING(RECOVERY_REQUEST) pot fi alocate."
            );
        }

        School school = (origSession.getSchool() != null) ? origSession.getSchool() : origGroup.getSchool();
        if (school == null) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Nu pot determina școala cererii."
            );
        }

        List<Session> targets = sessionRepository.findPlannedFutureRecoveryTargetsBySchool(
                school,
                LocalDate.now(),
                SessionStatus.PLANNED
        );

        List<TeacherRecoveryTargetSessionResponse> out = new ArrayList<>();

        for (Session s : targets) {
            if (s.getIdSession() == origSession.getIdSession()) continue;

            GroupClass g = s.getGroup();
            if (g == null) continue;

            Integer max = g.getMaxRecoverySlots();
            if (max == null || max <= 0) continue;

            long used = attendanceRepository.countRecoveryForSession(s);
            if (used >= max) continue;

            User t = g.getTeacher();

            out.add(new TeacherRecoveryTargetSessionResponse(
                    s.getIdSession(),
                    s.getSessionDate(),
                    s.getTime(),
                    g.getIdGroup(),
                    g.getGroupName(),
                    g.getCourse() != null ? g.getCourse().getName() : null,
                    t != null ? t.getIdUser() : 0,
                    t != null ? t.getLastName() + " " + t.getFirstName() : null,
                    s.getSchool() != null
                            ? s.getSchool().getName()
                            : (g.getSchool() != null ? g.getSchool().getName() : null),
                    used,
                    max
            ));
        }

        return out;
    }

    @Override
    @Transactional
    public void confirmCancelRequest(String authenticatedEmail, int attendanceId) {
        User teacher = getAuthenticatedTeacher(authenticatedEmail);

        Attendance a = attendanceRepository.findById(attendanceId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SESSION_NOT_FOUND,
                        "Attendance not found."
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
                        && note.trim().startsWith("CANCEL_REQUEST");

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
            if (nota.startsWith("CANCEL_REQUEST")) return AttendanceStatus.CANCELLED_BY_PARENT;
            if (nota.startsWith("RECOVERY_REQUEST")) return AttendanceStatus.RECOVERY_REQUESTED;
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

    private TeacherParentRequestResponse mapToParentRequestResponse(Attendance a, AttendanceStatus type) {
        Session s = a.getSession();
        GroupClass g = s != null ? s.getGroup() : null;
        Child child = a.getChild();
        User parent = child != null ? child.getParent() : null;

        String parentName = parent != null ? parent.getLastName() + " " + parent.getFirstName() : null;
        String childName = child != null ? child.getChildLastName() + " " + child.getChildFirstName() : null;

        return new TeacherParentRequestResponse(
                a.getIdAttendance(),
                child != null ? child.getIdChild() : 0,
                childName,
                parentName,
                parent != null ? parent.getPhone() : null,
                parent != null ? parent.getEmail() : null,
                s != null ? s.getIdSession() : 0,
                s != null ? s.getSessionDate() : null,
                s != null ? s.getTime() : null,
                g != null ? g.getGroupName() : null,
                g != null && g.getCourse() != null ? g.getCourse().getName() : null,
                g != null && g.getSchool() != null ? g.getSchool().getName() : null,
                type != null ? type.name() : null,
                a.getNota(),
                g != null ? g.getIdGroup() : 0,
                a.getAssignedToSessionId(),
                a.getStatus() != null ? a.getStatus().name() : null
        );
    }
}