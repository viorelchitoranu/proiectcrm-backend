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

    private static final String GROUP_NOT_FOUND   = "Group not found";
    private static final String COURSE_NOT_FOUND  = "Course not found";
    private static final String SCHOOL_NOT_FOUND  = "School not found";
    private static final String TEACHER_NOT_FOUND = "Teacher not found";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH.mm");

    private static final String SESIUNEA = "Sesiunea ";

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
                .orElseThrow(() -> new IllegalArgumentException(GROUP_NOT_FOUND));
        return toAdminResponse(g); // Mapare la response.
    }

    // Grupe pentru un anumit curs
    @Override
    public List<GroupAdminResponse> getGroupsByCourse(int courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException(COURSE_NOT_FOUND));
        return groupClassRepository.findByCourse(course)
                .stream()
                .map(this::toAdminResponse)
                .toList();
    }

    // Porneste grupa
    @Override
    public GroupAdminResponse startGroup(int groupId, GroupStartRequest request) {
        GroupClass group = groupClassRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException(GROUP_NOT_FOUND));

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
                .orElseThrow(() -> new IllegalArgumentException(COURSE_NOT_FOUND));

        School school = schoolRepository.findById(request.getSchoolId())
                .orElseThrow(() -> new IllegalArgumentException(SCHOOL_NOT_FOUND));

        User teacher = userRepository.findById(request.getTeacherId())
                .orElseThrow(() -> new IllegalArgumentException(TEACHER_NOT_FOUND));

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


        DateTimeFormatter dateFormatter = DATE_FORMATTER;
        DateTimeFormatter timeFormatter = TIME_FORMATTER;

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
                        SESIUNEA + sessionIndex +
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

    /**
     * Actualizează o grupă existentă — câmpuri simple, profesor, școală, dată sfârșit.
     *
     * REFACTORIZARE (SonarCloud — Cognitive Complexity):
     *   Metoda originală avea complexitate 39 (limita e 15).
     *   Logica a fost extrasă în 3 metode private:
     *     applySimpleFieldUpdates()  → name, capacity, price, recovery, active
     *     applyTeacherChange()       → schimbare profesor + update TeacherSession-uri viitoare
     *     applySchoolChange()        → schimbare școală + update sesiuni viitoare PLANNED
     *   applyEndDateChange() exista deja ca metodă privată separată.
     */
    @Override
    @Transactional
    public GroupAdminResponse updateGroup(int groupId, GroupUpdateRequest request) {

        GroupClass group = groupClassRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException(GROUP_NOT_FOUND));

        // Pasul 1: câmpuri simple — name, capacity, price, recovery, active
        boolean nameChanged = applySimpleFieldUpdates(group, request);

        // Pasul 2: schimbare profesor (dacă e cerut)
        if (request.getTeacherId() != null) {
            applyTeacherChange(group, request.getTeacherId());
        }

        // Pasul 3: schimbare școală (dacă e cerută)
        if (request.getSchoolId() != null) {
            applySchoolChange(group, request.getSchoolId());
        }

        // Pasul 4: schimbare dată sfârșit (dacă e cerută)
        if (request.getEndDate() != null) {
            applyEndDateChange(group, request.getEndDate());
        }

        GroupClass saved = groupClassRepository.save(group);

        // Pasul 5: redenumire sesiuni viitoare PLANNED dacă s-a schimbat numele grupei
        if (nameChanged) {
            renameFuturePlannedSessions(saved);
        }

        // Pasul 6: recalcul preț total
        if (saved.getSessionPrice() != null) {
            long totalSessions = sessionRepository.countByGroup(saved);
            saved.setTotalPrice(saved.getSessionPrice() * totalSessions);
            saved = groupClassRepository.save(saved);
        }

        return toAdminResponse(saved);
    }

    /**
     * Aplică actualizările câmpurilor simple ale grupei.
     * Validează că noua capacitate nu e mai mică decât numărul de copii înscriși activi.
     *
     * @return true dacă numele grupei s-a schimbat (necesită redenumire sesiuni)
     */
    private boolean applySimpleFieldUpdates(GroupClass group, GroupUpdateRequest request) {
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
            // maxCapacity == 0 înseamnă "nelimitat" — nu validăm
            if (request.getMaxCapacity() > 0) {
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
        }

        if (request.getMaxRecoverySlots() != null) group.setMaxRecoverySlots(request.getMaxRecoverySlots());
        if (request.getActive() != null)           group.setIsActive(request.getActive());
        if (request.getSessionPrice() != null)     group.setSessionPrice(request.getSessionPrice());

        return nameChanged;
    }

    /**
     * Schimbă profesorul grupei dacă e diferit de cel curent.
     * Actualizează și TeacherSession-urile MAIN pentru sesiunile viitoare PLANNED.
     *
     * Validări:
     *   - Profesorul nou există în BD
     *   - Are rol TEACHER
     *   - Este activ
     *
     * @param group      grupa de actualizat
     * @param teacherId  ID-ul noului profesor
     */
    private void applyTeacherChange(GroupClass group, int teacherId) {
        User oldTeacher = group.getTeacher();
        if (oldTeacher != null && oldTeacher.getIdUser() == teacherId) {
            return; // același profesor — nimic de făcut
        }

        User newTeacher = userRepository.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException(TEACHER_NOT_FOUND));

        if (!"TEACHER".equalsIgnoreCase(newTeacher.getRole().getRoleName())) {
            throw new IllegalStateException("User-ul selectat nu are rol de TEACHER.");
        }
        if (Boolean.FALSE.equals(newTeacher.getActive())) {
            throw new IllegalStateException("Profesorul este INACTIV. Activează-l sau alege alt profesor.");
        }

        group.setTeacher(newTeacher);

        // Actualizare TeacherSession-uri MAIN pentru sesiunile viitoare PLANNED
        var futurePlanned = sessionRepository.findByGroupAndSessionDateGreaterThanEqualAndSessionStatus(
                group, LocalDate.now(), SessionStatus.PLANNED);

        if (!futurePlanned.isEmpty() && oldTeacher != null) {
            var oldLinks = teacherSessionRepository.findBySessionInAndTeacherAndTeachingRole(
                    futurePlanned, oldTeacher, TeachingRole.MAIN);
            oldLinks.forEach(ts -> ts.setTeacher(newTeacher));
            teacherSessionRepository.saveAll(oldLinks);
        }
    }

    /**
     * Schimbă școala grupei dacă e diferită de cea curentă.
     * Actualizează și sesiunile viitoare PLANNED cu noua școală.
     *
     * @param group    grupa de actualizat
     * @param schoolId ID-ul noii școli
     */
    private void applySchoolChange(GroupClass group, int schoolId) {
        if (group.getSchool() != null && group.getSchool().getIdSchool() == schoolId) {
            return; // aceeași școală — nimic de făcut
        }

        School school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new IllegalArgumentException(SCHOOL_NOT_FOUND));

        group.setSchool(school);

        // Actualizare sesiuni viitoare PLANNED cu noua școală
        var futurePlanned = sessionRepository.findByGroupAndSessionDateGreaterThanEqualAndSessionStatus(
                group, LocalDate.now(), SessionStatus.PLANNED);

        futurePlanned.forEach(s -> s.setSchool(school));
        sessionRepository.saveAll(futurePlanned);
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

        DateTimeFormatter dateFormatter = DATE_FORMATTER;
        DateTimeFormatter timeFormatter = TIME_FORMATTER;

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
                    session.setName(SESIUNEA + sessionIndex + " – " + group.getGroupName() + " – " + dateText + ", " + timeText);

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

        DateTimeFormatter dateFormatter = DATE_FORMATTER;
        DateTimeFormatter timeFormatter = TIME_FORMATTER;

        int idx = 1;
        for (Session s : sessions) {
            String dateText = s.getSessionDate().format(dateFormatter);
            String timeText = s.getTime().format(timeFormatter);
            s.setName(SESIUNEA + idx + " – " + group.getGroupName() + " – " + dateText + ", " + timeText);
            idx++;
        }
        sessionRepository.saveAll(sessions);
    }

    @Override
    public List<SessionAdminResponse> getGroupSessions(int groupId) {

        GroupClass group = groupClassRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException(GROUP_NOT_FOUND));

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
                .orElseThrow(() -> new IllegalArgumentException(GROUP_NOT_FOUND));


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
                .orElseThrow(() -> new IllegalArgumentException(GROUP_NOT_FOUND));

        List<Session> nonTaught = sessionRepository.findByGroupAndSessionStatusIn(group, NON_TAUGHT_STATUSES);
        if (nonTaught.isEmpty()) return;

        long attCount = attendanceRepository.countBySessionIn(nonTaught);
        if (attCount > 0) {
            throw new IllegalStateException("Purge blocat: există attendance pe sesiuni non-taught.");
        }

        teacherSessionRepository.deleteBySessionIn(nonTaught);
        sessionRepository.deleteAll(nonTaught);
    }

    /**
     * Șterge complet o grupă inactivă sau finalizată, arhivând toate attendance-urile.
     *
     * REFACTORIZARE (SonarCloud — Cognitive Complexity):
     *   Metoda originală avea complexitate 45 (limita e 15) din cauza lambda-ului
     *   mare de mapping și logicii de ștergere inline.
     *   Logica a fost extrasă în 2 metode private:
     *     toArchiveRecordForGroupDelete() → conversie Attendance → AttendanceArchive
     *     deleteGroupEntities()           → ștergere în ordinea corectă (FK constraints)
     */
    @Override
    @Transactional
    public GroupDeleteSafeResponse deleteGroupSafe(int groupId) {

        GroupClass group = groupClassRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException(GROUP_NOT_FOUND));

        // Validare: ștergerea e permisă doar dacă grupa e inactivă sau finalizată
        boolean isActive = Boolean.TRUE.equals(group.getIsActive());
        LocalDate end = group.getGroupEndDate();
        boolean finished = (end != null) && LocalDate.now().isAfter(end);

        if (isActive && !finished) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Delete Safe permis doar dacă grupa este inactivă sau FINISHED (azi > endDate)."
            );
        }

        List<Session> sessions      = sessionRepository.findByGroup(group);
        List<Attendance> attendances = attendanceRepository.findBySession_Group(group);

        // Arhivare attendance-uri înainte de ștergere
        LocalDateTime now = LocalDateTime.now();
        List<AttendanceArchive> archives = attendances.stream()
                .map(a -> toArchiveRecordForGroupDelete(a, group, now))
                .toList();

        attendanceArchiveRepository.saveAll(archives);

        // Ștergere în ordinea corectă — respectă FK constraints
        long[] counts = deleteGroupEntities(group, sessions, attendances);

        return new GroupDeleteSafeResponse(
                groupId,
                archives.size(),
                counts[0],  // deletedAttendances
                counts[1],  // deletedTeacherSessions
                counts[2],  // deletedSessions
                counts[3],  // deletedChildGroupLinks
                "Delete Safe: attendance arhivat, apoi grupă + sesiuni + legături șterse."
        );
    }

    /**
     * Convertește un Attendance în AttendanceArchive pentru ștergerea completă a grupei.
     *
     * Diferență față de toArchiveRecord() din AttendanceArchiveServiceImpl:
     *   Când Session este null (date inconsistente), folosim grupa curentă ca fallback
     *   pentru a păstra informațiile grupei în arhivă.
     *
     * @param a     attendance-ul de arhivat
     * @param group grupa care se șterge (folosit ca fallback dacă session e null)
     * @param now   timestamp-ul arhivării
     * @return      înregistrarea de arhivă completată
     */
    /**
     * Convertește un Attendance în AttendanceArchive pentru ștergerea completă a grupei.
     *
     * REFACTORIZARE (SonarCloud — Cognitive Complexity):
     *   Metoda originală avea complexitate 22 (limita e 15) din cauza celor 15+
     *   expresii ternare (null checks) inline.
     *   Setter-urile au fost grupate în 3 metode private cu responsabilitate unică:
     *     fillGroupSessionFieldsForDelete() → câmpuri grupă + sesiune cu fallback pe group
     *     fillChildParentFieldsForDelete()  → câmpuri copil + părinte (GDPR snapshot)
     *     fillOriginalFieldsForDelete()     → câmpurile originale ale attendance-ului
     *
     * Diferență față de toArchiveRecord() din AttendanceArchiveServiceImpl:
     *   Când Session este null (date inconsistente), folosim grupa curentă ca fallback
     *   pentru a păstra informațiile grupei în arhivă — util la ștergere completă.
     *
     * @param a     attendance-ul de arhivat
     * @param group grupa care se șterge (folosit ca fallback dacă session e null)
     * @param now   timestamp-ul arhivării
     * @return      înregistrarea de arhivă completată
     */
    private AttendanceArchive toArchiveRecordForGroupDelete(
            Attendance a, GroupClass group, LocalDateTime now) {

        Session s    = a.getSession();
        GroupClass g = (s != null) ? s.getGroup() : group;
        Child child  = a.getChild();
        User parent  = (child != null) ? child.getParent() : null;

        AttendanceArchive ar = new AttendanceArchive();

        // IDs originale — pentru trasat înapoi la înregistrările originale dacă e nevoie
        ar.setOriginalAttendanceId(a.getIdAttendance());
        ar.setOriginalSessionId(s != null ? s.getIdSession() : null);
        // fallback pe group.getIdGroup() dacă g e null — garantează că grupul e mereu prezent în arhivă
        ar.setOriginalGroupId(g != null ? g.getIdGroup() : group.getIdGroup());

        fillGroupSessionFieldsForDelete(ar, g, s, group);  // snapshot grupă + sesiune cu fallback
        fillChildParentFieldsForDelete(ar, child, parent); // snapshot copil + părinte (GDPR)
        fillOriginalFieldsForDelete(ar, a, now);           // câmpuri originale attendance + metadata

        return ar;
    }

    /**
     * Completează câmpurile grupei și sesiunii în arhivă cu fallback pe grupa ștearsă.
     *
     * Diferență față de fillGroupSessionFields() din AttendanceArchiveServiceImpl:
     *   groupName și groupId folosesc `group` ca fallback când `g` e null — asigură
     *   că arhiva păstrează referința la grupă chiar și când sesiunea nu mai are grupă.
     *
     * courseName/schoolName/teacherName: null dacă lipsesc relațiile.
     * sessionDate/Time/Status/Type: null dacă attendance-ul nu are sesiune (date vechi).
     *
     * @param ar    arhiva de completat
     * @param g     grupa sesiunii (poate fi null — se folosește fallback)
     * @param s     sesiunea originală (poate fi null pentru date vechi)
     * @param group grupa care se șterge — fallback când g e null
     */
    private void fillGroupSessionFieldsForDelete(
            AttendanceArchive ar, GroupClass g, Session s, GroupClass group) {
        // groupName: fallback pe group.getGroupName() dacă g e null
        ar.setGroupName(g != null ? g.getGroupName() : group.getGroupName());
        ar.setCourseName(g != null && g.getCourse()   != null ? g.getCourse().getName()   : null);
        ar.setSchoolName(g != null && g.getSchool()   != null ? g.getSchool().getName()   : null);
        ar.setTeacherName(g != null && g.getTeacher() != null
                ? g.getTeacher().getLastName() + " " + g.getTeacher().getFirstName()
                : null);

        ar.setSessionDate(s != null ? s.getSessionDate()    : null);
        ar.setSessionTime(s != null ? s.getTime()           : null);
        ar.setSessionStatus(s != null ? s.getSessionStatus() : null);
        ar.setSessionType(s != null ? s.getSessionType()     : null);
    }

    /**
     * Completează câmpurile copilului și părintelui în arhivă.
     *
     * GDPR: stocăm snapshot-ul datelor personale la momentul arhivării.
     * Dacă contul de părinte e șters ulterior, arhiva păstrează datele
     * pentru audit și raportare istorică.
     *
     * parentName: concatenare LastName + FirstName, null dacă copilul nu are părinte.
     *
     * @param ar     arhiva de completat
     * @param child  copilul asociat attendance-ului (poate fi null)
     * @param parent părintele copilului (poate fi null)
     */
    private void fillChildParentFieldsForDelete(
            AttendanceArchive ar, Child child, User parent) {
        ar.setChildId(child != null ? child.getIdChild()            : null);
        ar.setChildFirstName(child != null ? child.getChildFirstName() : null);
        ar.setChildLastName(child != null ? child.getChildLastName()   : null);

        ar.setParentId(parent != null ? parent.getIdUser()                               : null);
        ar.setParentName(parent != null ? parent.getLastName() + " " + parent.getFirstName() : null);
        ar.setParentEmail(parent != null ? parent.getEmail()  : null);
        ar.setParentPhone(parent != null ? parent.getPhone()  : null);
    }

    /**
     * Completează câmpurile originale ale attendance-ului și metadata arhivării.
     *
     * Câmpurile originale sunt copiate 1:1 din attendance — nu se modifică nimic.
     * archivedAt: timestamp calculat o dată pentru toată lista (nu LocalDateTime.now()
     * per element) pentru consistență și performanță.
     *
     * @param ar  arhiva de completat
     * @param a   attendance-ul original
     * @param now timestamp arhivării (calculat o dată în deleteGroupSafe)
     */
    private void fillOriginalFieldsForDelete(
            AttendanceArchive ar, Attendance a, LocalDateTime now) {
        ar.setAttendanceStatus(a.getStatus());
        ar.setNota(a.getNota());
        ar.setCreatedAt(a.getCreatedAt());
        ar.setUpdatedAt(a.getUpdatedAt());
        ar.setRecovery(a.isRecovery());
        ar.setRecoveryForSessionId(a.getRecoveryForSessionId());
        ar.setArchivedAt(now);
    }

    /**
     * Șterge toate entitățile legate de o grupă în ordinea corectă (FK constraints):
     *   1. Attendance (depinde de Session)
     *   2. TeacherSession (depinde de Session)
     *   3. Session (depinde de GroupClass)
     *   4. ChildGroup (depinde de GroupClass)
     *   5. GroupClass
     *
     * @return array cu [deletedAttendances, deletedTeacherSessions,
     *                   deletedSessions, deletedChildGroupLinks]
     */
    private long[] deleteGroupEntities(
            GroupClass group, List<Session> sessions, List<Attendance> attendances) {

        long deletedAttendances = attendances.size();
        attendanceRepository.deleteAll(attendances);

        long deletedTeacherSessions = 0L;
        if (!sessions.isEmpty()) {
            teacherSessionRepository.deleteBySessionIn(sessions);
            deletedTeacherSessions = sessions.size();
        }

        long deletedSessions = sessions.size();
        sessionRepository.deleteAll(sessions);

        childGroupRepository.deleteByGroup(group);
        long deletedChildGroupLinks = 0L; // valoarea originală — numărul nu e folosit în răspuns

        groupClassRepository.delete(group);

        return new long[]{
                deletedAttendances,
                deletedTeacherSessions,
                deletedSessions,
                deletedChildGroupLinks
        };
    }






}



