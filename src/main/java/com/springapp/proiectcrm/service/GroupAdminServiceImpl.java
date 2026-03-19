package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.*;
import com.springapp.proiectcrm.model.*;
import com.springapp.proiectcrm.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.List;
import java.util.Map;
import java.util.Set;

import java.util.stream.Collectors;
import com.springapp.proiectcrm.exception.BusinessException;
import com.springapp.proiectcrm.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class GroupAdminServiceImpl implements GroupAdminService {


    private final CourseRepository courseRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final GroupClassRepository groupClassRepository;
    private final SessionRepository sessionRepository;
    private final HolidayRepository holidayRepository;
    private final TeacherSessionRepository teacherSessionRepository;
    private final ChildGroupRepository childGroupRepository;
    private final AttendanceRepository attendanceRepository;
    private final AttendanceArchiveRepository attendanceArchiveRepository;




    private static final List<SessionStatus> NON_TAUGHT_STATUSES = List.of(
            SessionStatus.PLANNED,
            SessionStatus.CANCELED,
            SessionStatus.CANCELED_HOLIDAY,
            SessionStatus.CANCELED_MANUAL,
            SessionStatus.NOT_STARTED_SKIPPED
    );

    private GroupAdminResponse toAdminResponse(GroupClass g) {
        String teacherName = (g.getTeacher() != null)
                ? g.getTeacher().getLastName() + " " + g.getTeacher().getFirstName()
                : null;

        String courseName = g.getCourse() != null ? g.getCourse().getName() : null;
        String schoolName = g.getSchool() != null ? g.getSchool().getName() : null;


        long totalSessions = sessionRepository.countByGroup(g);


        long heldSessions = sessionRepository.countByGroupAndSessionStatus(g, SessionStatus.TAUGHT);
        long cancelledSessions = sessionRepository.countByGroupAndSessionStatusIn(
                g,
                List.of(SessionStatus.CANCELED, SessionStatus.CANCELED_HOLIDAY, SessionStatus.CANCELED_MANUAL)
        );


        int progressPercent = 0;
        if (totalSessions > 0) {
            progressPercent = (int) Math.round(heldSessions * 100.0 / totalSessions);
        }

        long activeChildren = childGroupRepository.countByGroupAndActiveTrue(g);

        String status = computeGroupStatus(g);

        Integer schoolId = g.getSchool() != null ? g.getSchool().getIdSchool() : null;
        Integer teacherId = g.getTeacher() != null ? g.getTeacher().getIdUser() : null;

        long usedRecoverySlots = attendanceRepository.countFutureRecovery(g, LocalDate.now());



        return new GroupAdminResponse(
                g.getIdGroup(),
                g.getGroupName(),
                courseName,
                schoolName,
                teacherName,
                g.getGroupStartDate(),
                g.getGroupEndDate(),
                g.getSessionStartTime(),
                g.getGroupMaxCapacity(),
                g.getSessionPrice(),
                g.getTotalPrice(),
                g.getIsActive(),
                g.getGroupCreatedAt(),
                g.getStartConfirmedAt(),
                totalSessions,
                heldSessions,
                cancelledSessions,
                progressPercent,
                activeChildren,
                status,
                teacherId,
                schoolId,
                g.getMaxRecoverySlots(),
                g.getForceStopAt(),
                usedRecoverySlots

        );
    }

    private String computeGroupStatus(GroupClass g) {
        java.time.LocalDate today = java.time.LocalDate.now();

        java.time.LocalDate startRef = null;
        if (g.getStartConfirmedAt() != null) {
            startRef = g.getStartConfirmedAt().toLocalDate();
        } else if (g.getGroupStartDate() != null) {
            startRef = g.getGroupStartDate();
        }

        java.time.LocalDate end = g.getGroupEndDate();

        if (end != null && today.isAfter(end)) {
            return "FINISHED";
        }

        if (startRef == null || today.isBefore(startRef)) {
            return "NOT_STARTED";
        }

        return "ONGOING";
    }


    // Returnează toate grupele pentru admin
    @Override
    public List<GroupAdminResponse> getAllGroups() {
        return groupClassRepository.findAll()
                .stream()
                .map(this::toAdminResponse) // Mapare fiecare GroupClass -> GroupAdminResponse.
                .toList();
    }

    // Returneaza o singura grupa
    @Override
    public GroupAdminResponse getGroup(int groupId) {
        GroupClass g = groupClassRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
        return toAdminResponse(g); // Mapare la response.
    }

    // Grupe pentru un anumit curs
    @Override
    public List<GroupAdminResponse> getGroupsByCourse(int courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));
        return groupClassRepository.findByCourse(course)
                .stream()
                .map(this::toAdminResponse)
                .toList();
    }

    // Porneste grupa
    @Override
    public GroupAdminResponse startGroup(int groupId, GroupStartRequest request) {
        GroupClass group = groupClassRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        if (group.getStartConfirmedAt() != null) {
            return toAdminResponse(group);
        }

        LocalDate effectiveDate = (request != null && request.getEffectiveStartDate() != null)
                ? request.getEffectiveStartDate()
                : LocalDate.now();

        // Setare startConfirmedAt
        group.setStartConfirmedAt(
                LocalDateTime.of(
                        effectiveDate,
                        group.getSessionStartTime() != null ? group.getSessionStartTime() : java.time.LocalTime.MIDNIGHT
                )
        );

        GroupClass saved = groupClassRepository.save(group);
        return toAdminResponse(saved);
    }

    @Transactional
    @Override
    public GroupAdminResponse createGroupWithSessions(GroupCreateRequest request) {
        Course course = courseRepository.findById(request.getCourseId())
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));

        School school = schoolRepository.findById(request.getSchoolId())
                .orElseThrow(() -> new IllegalArgumentException("School not found"));

        User teacher = userRepository.findById(request.getTeacherId())
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        if (!"TEACHER".equalsIgnoreCase(teacher.getRole().getRoleName())) {
            throw new IllegalStateException("User-ul selectat nu are rol de TEACHER.");
        }

        GroupClass group = new GroupClass();
        group.setCourse(course);
        group.setSchool(school);
        group.setTeacher(teacher);
        group.setGroupName(request.getName());
        group.setGroupStartDate(request.getStartDate());
        group.setGroupEndDate(request.getEndDate());
        group.setSessionStartTime(request.getSessionStartTime());
        group.setGroupMaxCapacity(request.getMaxCapacity() != null ? request.getMaxCapacity() : 0);
        group.setSessionPrice(request.getSessionPrice());
        group.setIsActive(request.getActive() == null ? Boolean.TRUE : request.getActive());
        group.setGroupCreatedAt(LocalDateTime.now());
        group.setMaxRecoverySlots(request.getMaxRecoverySlots());
        group.setTotalPrice(0.0);

        GroupClass savedGroup = groupClassRepository.save(group);

        long sessionsCount = generateSessionsForGroup(savedGroup, teacher);

        if (savedGroup.getSessionPrice() != null) {
            savedGroup.setTotalPrice(savedGroup.getSessionPrice() * sessionsCount);
            savedGroup = groupClassRepository.save(savedGroup);
        }

        // 4) Returnare DTO final
        return toAdminResponse(savedGroup);
    }

    private long generateSessionsForGroup(GroupClass group, User teacher) {
        LocalDate start = group.getGroupStartDate();
        LocalDate end = group.getGroupEndDate();

        if (start == null || end == null || end.isBefore(start)) {
            throw new IllegalArgumentException("Perioadă invalidă pentru grupă.");
        }

        // Zile libere (holidays)
        List<Holiday> holidays = holidayRepository.findByHolidayDateBetween(start, end);
        Set<LocalDate> holidayDates = holidays.stream()
                .map(Holiday::getHolidayDate)
                .collect(java.util.stream.Collectors.toSet());


        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH.mm");

        LocalDate current = start;

        int sessionIndex = 1;

        while (!current.isAfter(end)) {
            // sar peste zilele libere
            if (!holidayDates.contains(current)) {

                Session session = new Session();

                session.setGroup(group);//
                session.setSchool(group.getSchool());//
                session.setSessionDate(current);//
                session.setTime(group.getSessionStartTime());//
                session.setSessionStatus(SessionStatus.PLANNED); // enum
                session.setSessionType(SessionType.REGULAR);//enum

                session.setSequenceNo(sessionIndex); // <-- IMPORTANT

                String dateText = current.format(dateFormatter);

                String timeText = group.getSessionStartTime().format(timeFormatter);

                session.setName(
                        "Sesiunea " + sessionIndex +
                                " – " + group.getGroupName() +
                                " – " + dateText +
                                ", " + timeText
                );

                session.setSessionCreatedAt(LocalDateTime.now());//

                Session savedSession = sessionRepository.save(session);

                TeacherSession ts = new TeacherSession();
                ts.setTeacher(teacher);
                ts.setSession(savedSession);
                ts.setTeachingRole(TeachingRole.MAIN);
                teacherSessionRepository.save(ts);


                sessionIndex++;
            }

            // saptamana cu saptamana
            current = current.plusWeeks(1);
        }

        return sessionIndex - 1L; // numar de sesiuni create

    }

    @Override
    @Transactional
    public GroupAdminResponse updateGroup(int groupId, GroupUpdateRequest request) {

        GroupClass group = groupClassRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        // 0) Update simple fields (name/capacity/price/recovery/active)
        boolean nameChanged = false;
        if (request.getName() != null) {
            String newName = request.getName().trim();
            if (!newName.equals(group.getGroupName())) {
                group.setGroupName(newName);
                nameChanged = true;
            }
        }
        if (request.getMaxCapacity() != null) {
            group.setGroupMaxCapacity(request.getMaxCapacity());
        }


        // MODUL 1 — patch pentru GroupAdminServiceImpl.updateGroup()
        // Adaugă validare anti-suprascriere capacitate imediat după blocul de update
        // al câmpurilor simple (name/capacity/price/recovery/active).
        // ── VALIDARE MODUL 1: capacitate nouă < locuri deja ocupate ──────────────────
        if (request.getMaxCapacity() != null && request.getMaxCapacity() > 0) {
            // maxCapacity == 0 înseamnă "nelimitat" — nu validăm
            long occupiedSlots = childGroupRepository.countByGroupAndActiveTrue(group);
            if (request.getMaxCapacity() < occupiedSlots) {
                throw new BusinessException(
                        ErrorCode.CAPACITY_BELOW_ENROLLED,
                        "Capacitatea nouă (" + request.getMaxCapacity() + ") este mai mică decât " +
                                "numărul de copii activi înscriși (" + occupiedSlots + "). " +
                                "Dezactivează mai întâi înscrierea unor copii sau alege o capacitate ≥ " + occupiedSlots + "."
                );
            }
        }


        if (request.getMaxRecoverySlots() != null) {
            group.setMaxRecoverySlots(request.getMaxRecoverySlots());
        }
        if (request.getActive() != null) {
            group.setIsActive(request.getActive());
        }
        if (request.getSessionPrice() != null) {
            group.setSessionPrice(request.getSessionPrice());
        }

        // 1) Teacher change
        if (request.getTeacherId() != null) {
            User oldTeacher = group.getTeacher();

            if (oldTeacher == null || oldTeacher.getIdUser() != request.getTeacherId()) {

                User newTeacher = userRepository.findById(request.getTeacherId())
                        .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

                if (!"TEACHER".equalsIgnoreCase(newTeacher.getRole().getRoleName())) {
                    throw new IllegalStateException("User-ul selectat nu are rol de TEACHER.");
                }

                Boolean tActive = newTeacher.getActive();
                if (tActive != null && !tActive) {
                    throw new IllegalStateException("Profesorul este INACTIV. Activează-l sau alege alt profesor.");
                }

                group.setTeacher(newTeacher);

                LocalDate today = LocalDate.now();
                var futurePlannedSessions =
                        sessionRepository.findByGroupAndSessionDateGreaterThanEqualAndSessionStatus(
                                group, today, SessionStatus.PLANNED
                        );

                if (!futurePlannedSessions.isEmpty()) {
                    var oldLinks = teacherSessionRepository.findBySessionInAndTeacherAndTeachingRole(
                            futurePlannedSessions, oldTeacher, TeachingRole.MAIN
                    );

                    for (TeacherSession ts : oldLinks) {
                        ts.setTeacher(newTeacher);
                    }
                    teacherSessionRepository.saveAll(oldLinks);
                }
            }
        }

        // 2) School change
        if (request.getSchoolId() != null) {
            if (group.getSchool() == null || group.getSchool().getIdSchool() != request.getSchoolId()) {

                School school = schoolRepository.findById(request.getSchoolId())
                        .orElseThrow(() -> new IllegalArgumentException("School not found"));

                group.setSchool(school);

                LocalDate today = LocalDate.now();
                var futurePlannedSessions =
                        sessionRepository.findByGroupAndSessionDateGreaterThanEqualAndSessionStatus(
                                group, today, SessionStatus.PLANNED
                        );

                for (Session s : futurePlannedSessions) {
                    s.setSchool(school);
                }
                sessionRepository.saveAll(futurePlannedSessions);
            }
        }

        // 3) endDate change
        if (request.getEndDate() != null) {
            applyEndDateChange(group, request.getEndDate());
        }

        GroupClass saved = groupClassRepository.save(group);

        // 4) Daca s-a schimbat numele grupei, update nume sesiuni viitoare PLANNED
        if (nameChanged) {
            renameFuturePlannedSessions(saved);
        }

        // 5) Recalcul totalPrice
        if (saved.getSessionPrice() != null) {
            long totalSessions = sessionRepository.countByGroup(saved);
            saved.setTotalPrice(saved.getSessionPrice() * totalSessions);
            saved = groupClassRepository.save(saved);
        }

        return toAdminResponse(saved);
    }

    private void applyEndDateChange(GroupClass group, LocalDate newEndDate) {

        LocalDate start = group.getGroupStartDate();
        if (start == null) throw new IllegalStateException("Group startDate is null.");
        if (newEndDate.isBefore(start)) {
            throw new IllegalArgumentException("endDate nu poate fi înainte de startDate.");
        }

        LocalDate oldEnd = group.getGroupEndDate();
        group.setGroupEndDate(newEndDate);


        if (oldEnd == null) return;

        // SCURTARE: stergere sesiunile PLANNED dupa noul endDate
        if (newEndDate.isBefore(oldEnd)) {
            List<Session> toDelete = sessionRepository.findByGroupAndSessionDateAfterAndSessionStatus(
                    group, newEndDate, SessionStatus.PLANNED
            );
            if (!toDelete.isEmpty()) {
                teacherSessionRepository.deleteBySessionIn(toDelete);
                sessionRepository.deleteAll(toDelete);
            }
            return;
        }

        // generare sesiuni noi după ultima sesiune existenta
        LocalDate from = sessionRepository.findTopByGroupOrderBySessionDateDesc(group)
                .map(s -> s.getSessionDate().plusWeeks(1))
                .orElse(start);

        generateSessionsWeeklyFrom(group, group.getTeacher(), from, newEndDate);
    }

    private void generateSessionsWeeklyFrom(GroupClass group, User teacher, LocalDate fromInclusive, LocalDate end) {

        LocalDate startDate = fromInclusive;

        List<Holiday> holidays = holidayRepository.findByHolidayDateBetween(startDate, end);
        Set<LocalDate> holidayDates = holidays.stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toSet());

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH.mm");

        int sessionIndex = sessionRepository.findTopByGroupOrderBySequenceNoDesc(group)
                .map(s -> s.getSequenceNo() + 1)
                .orElse(1);

        LocalDate current = startDate;

        while (!current.isAfter(end)) {

            if (!holidayDates.contains(current)) {

                boolean exists = sessionRepository.existsByGroupAndSessionDate(group, current);
                if (!exists) {
                    Session session = new Session();
                    session.setGroup(group);
                    session.setSchool(group.getSchool());
                    session.setSessionDate(current);
                    session.setTime(group.getSessionStartTime());
                    session.setSessionStatus(SessionStatus.PLANNED);
                    session.setSessionType(SessionType.REGULAR);

                    session.setSequenceNo(sessionIndex);

                    String dateText = current.format(dateFormatter);
                    String timeText = group.getSessionStartTime().format(timeFormatter);
                    session.setName("Sesiunea " + sessionIndex + " – " + group.getGroupName() + " – " + dateText + ", " + timeText);

                    session.setSessionCreatedAt(LocalDateTime.now());

                    Session savedSession = sessionRepository.save(session);

                    if (teacher != null) {
                        TeacherSession ts = new TeacherSession();
                        ts.setTeacher(teacher);
                        ts.setSession(savedSession);
                        ts.setTeachingRole(TeachingRole.MAIN);
                        ts.setCreatedAt(LocalDateTime.now());
                        teacherSessionRepository.save(ts);
                    }

                    sessionIndex++;
                }
            }

            current = current.plusWeeks(1);
        }
    }



    private void renameFuturePlannedSessions(GroupClass group) {
        LocalDate today = LocalDate.now();
        var sessions = sessionRepository.findByGroupAndSessionDateGreaterThanEqualAndSessionStatus(
                group, today, SessionStatus.PLANNED
        );
        if (sessions.isEmpty()) return;

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH.mm");

        int idx = 1;
        for (Session s : sessions) {
            String dateText = s.getSessionDate().format(dateFormatter);
            String timeText = s.getTime().format(timeFormatter);
            s.setName("Sesiunea " + idx + " – " + group.getGroupName() + " – " + dateText + ", " + timeText);
            idx++;
        }
        sessionRepository.saveAll(sessions);
    }

    @Override
    public List<SessionAdminResponse> getGroupSessions(int groupId) {

        GroupClass group = groupClassRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        List<Session> sessions = sessionRepository.findByGroupOrderBySessionDateAsc(group);
        if (sessions.isEmpty()) return List.of();

        // teacher MAIN per session (din teacher_session)
        var mainLinks = teacherSessionRepository.findBySessionInAndTeachingRole(sessions, TeachingRole.MAIN);
        Map<Integer, User> teacherBySessionId = mainLinks.stream()
                .collect(Collectors.toMap(
                        ts -> ts.getSession().getIdSession(),
                        TeacherSession::getTeacher,
                        (a, b) -> a
                ));

        return sessions.stream().map(s -> {
            User t = teacherBySessionId.get(s.getIdSession());
            String teacherName = (t != null) ? (t.getLastName() + " " + t.getFirstName()) : null;

            return new SessionAdminResponse(
                    s.getIdSession(),
                    s.getName(),
                    s.getSessionDate(),
                    s.getTime(),
                    s.getSessionStatus(),
                    s.getSessionType(),
                    s.getSchool() != null ? s.getSchool().getIdSchool() : null,
                    s.getSchool() != null ? s.getSchool().getName() : null,
                    t != null ? t.getIdUser() : null,
                    teacherName
            );
        }).toList();
    }


    @Override
    @Transactional
    public GroupAdminResponse stopGroup(int groupId, GroupStopRequest request) {

        GroupClass group = groupClassRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));


        if (group.getForceStopAt() != null) {
            return toAdminResponse(group);
        }

        // 1) Oprire grupa
        group.setIsActive(Boolean.FALSE);
        group.setForceStopAt(LocalDateTime.now());


        LocalDate effectiveDate = (request != null && request.getEffectiveDate() != null)
                ? request.getEffectiveDate()
                : LocalDate.now();

        // 2) Anulare sesiunile viitoare PLANNED => CANCELED_MANUAL
        List<Session> futurePlanned = sessionRepository
                .findByGroupAndSessionDateGreaterThanEqualAndSessionStatus(
                        group,
                        effectiveDate,
                        SessionStatus.PLANNED
                );

        for (Session s : futurePlanned) {
            s.setSessionStatus(SessionStatus.CANCELED_MANUAL);
        }
        sessionRepository.saveAll(futurePlanned);

        GroupClass saved = groupClassRepository.save(group);

        return toAdminResponse(saved);
    }

    @Transactional
    @Override
    public void purgeNonTaughtSessions(int groupId) {
        GroupClass group = groupClassRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        List<Session> nonTaught = sessionRepository.findByGroupAndSessionStatusIn(group, NON_TAUGHT_STATUSES);
        if (nonTaught.isEmpty()) return;

        long attCount = attendanceRepository.countBySessionIn(nonTaught);
        if (attCount > 0) {
            throw new IllegalStateException("Purge blocat: există attendance pe sesiuni non-taught.");
        }

        teacherSessionRepository.deleteBySessionIn(nonTaught);
        sessionRepository.deleteAll(nonTaught);
    }

    @Override
    @Transactional
    public GroupDeleteSafeResponse deleteGroupSafe(int groupId) {

        GroupClass group = groupClassRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        //  (azi > endDate)
        boolean isActive = Boolean.TRUE.equals(group.getIsActive());
        LocalDate end = group.getGroupEndDate();
        boolean finished = (end != null) && LocalDate.now().isAfter(end);

        if (isActive && !finished) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Delete Safe permis doar dacă grupa este inactivă sau FINISHED (azi > endDate)."
            );
        }

        //  toate sesiunile grupei
        List<Session> sessions = sessionRepository.findByGroup(group);

        // toate attendance-urile pentru această grupa (inclusiv pe sesiuni non-TAUGHT)
        List<Attendance> attendances = attendanceRepository.findBySession_Group(group);

        // arhivare attendance-urile
        LocalDateTime now = LocalDateTime.now();

        List<AttendanceArchive> archives = attendances.stream().map(a -> {
            Session s = a.getSession();
            GroupClass g = (s != null) ? s.getGroup() : group;

            Child child = a.getChild();
            User parent = (child != null) ? child.getParent() : null;

            String courseName = (g != null && g.getCourse() != null) ? g.getCourse().getName() : null;
            String schoolName = (g != null && g.getSchool() != null) ? g.getSchool().getName() : null;
            String teacherName = (g != null && g.getTeacher() != null)
                    ? g.getTeacher().getLastName() + " " + g.getTeacher().getFirstName()
                    : null;

            AttendanceArchive ar = new AttendanceArchive();
            ar.setOriginalAttendanceId(a.getIdAttendance());
            ar.setOriginalSessionId(s != null ? s.getIdSession() : null);
            ar.setOriginalGroupId(g != null ? g.getIdGroup() : group.getIdGroup());

            ar.setGroupName(g != null ? g.getGroupName() : group.getGroupName());
            ar.setCourseName(courseName);
            ar.setSchoolName(schoolName);
            ar.setTeacherName(teacherName);

            ar.setSessionDate(s != null ? s.getSessionDate() : null);
            ar.setSessionTime(s != null ? s.getTime() : null);
            ar.setSessionStatus(s != null ? s.getSessionStatus() : null);
            ar.setSessionType(s != null ? s.getSessionType() : null);

            ar.setChildId(child != null ? child.getIdChild() : null);
            ar.setChildFirstName(child != null ? child.getChildFirstName() : null);
            ar.setChildLastName(child != null ? child.getChildLastName() : null);

            ar.setParentId(parent != null ? parent.getIdUser() : null);
            ar.setParentName(parent != null ? (parent.getLastName() + " " + parent.getFirstName()) : null);
            ar.setParentEmail(parent != null ? parent.getEmail() : null);
            ar.setParentPhone(parent != null ? parent.getPhone() : null);

            ar.setAttendanceStatus(a.getStatus());
            ar.setNota(a.getNota());
            ar.setCreatedAt(a.getCreatedAt());
            ar.setUpdatedAt(a.getUpdatedAt());
            ar.setRecovery(a.isRecovery());
            ar.setRecoveryForSessionId(a.getRecoveryForSessionId());

            ar.setArchivedAt(now);
            return ar;
        }).toList();

        attendanceArchiveRepository.saveAll(archives);

        // stergere attendance-urile originale (altfel nu se pot sterge sessions )
        long deletedAttendances = attendances.size();
        attendanceRepository.deleteAll(attendances);

        // stergere TeacherSession links pentru sesiunile grupei
        long deletedTeacherSessions = 0L;
        if (!sessions.isEmpty()) {
            teacherSessionRepository.deleteBySessionIn(sessions);
        }

        // stergem sesiunile
        long deletedSessions = sessions.size();
        sessionRepository.deleteAll(sessions);

        // stergere inscrierile copil-grupa
        long deletedChildGroupLinks = 0L;
        childGroupRepository.deleteByGroup(group);

        // stergere grupa
        groupClassRepository.delete(group);

        return new GroupDeleteSafeResponse(
                groupId,
                archives.size(),
                deletedAttendances,
                deletedTeacherSessions,
                deletedSessions,
                deletedChildGroupLinks,
                "Delete Safe: attendance arhivat, apoi grupă + sesiuni + legături șterse."
        );
    }





}



