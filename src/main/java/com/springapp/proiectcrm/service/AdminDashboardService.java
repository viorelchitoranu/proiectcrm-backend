package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.DashboardStatsResponse;
import com.springapp.proiectcrm.model.*;
import com.springapp.proiectcrm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Serviciu pentru datele dashboard-ului admin.
 * Returnează toate datele pentru cele 5 grafice recharts.
 */
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final AttendanceRepository attendanceRepository;
    private final ChildGroupRepository childGroupRepository;
    private final ChildRepository      childRepository;
    private final GroupClassRepository groupClassRepository;
    private final UserRepository       userRepository;
    private final RoleRepository       roleRepository;
    private final WaitlistRepository   waitlistRepository;
    private final SessionRepository    sessionRepository;

    private static final DateTimeFormatter MONTH_FORMATTER =
            DateTimeFormatter.ofPattern("MMM yyyy", new Locale("ro", "RO"));

    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats() {
        return new DashboardStatsResponse(
                getAttendanceByMonth(),
                getEnrollmentsByGroup(),
                getChildrenStats(),
                getTeacherGroups(),
                getWaitlistStats()
        );
    }

    private List<DashboardStatsResponse.MonthlyAttendance> getAttendanceByMonth() {
        List<DashboardStatsResponse.MonthlyAttendance> result = new ArrayList<>();
        LocalDate now = LocalDate.now();

        for (int i = 5; i >= 0; i--) {
            LocalDate monthStart = now.minusMonths(i).withDayOfMonth(1);
            LocalDate monthEnd   = monthStart.withDayOfMonth(monthStart.lengthOfMonth());

            List<Session> sessions = sessionRepository
                    .findBySessionDateBetweenAndSessionStatus(
                            monthStart, monthEnd, SessionStatus.TAUGHT);

            long present   = attendanceRepository.countBySessionIn(sessions);
            long sessCount = sessions.size();

            result.add(new DashboardStatsResponse.MonthlyAttendance(
                    monthStart.format(MONTH_FORMATTER), present, sessCount));
        }
        return result;
    }

    private List<DashboardStatsResponse.GroupEnrollment> getEnrollmentsByGroup() {
        return groupClassRepository.findAll().stream()
                .map(group -> new DashboardStatsResponse.GroupEnrollment(
                        group.getGroupName(),
                        group.getCourse() != null ? group.getCourse().getName() : "—",
                        childGroupRepository.countByGroupAndActiveTrue(group)
                ))
                .filter(g -> g.enrolled() > 0)
                .sorted(Comparator.comparingLong(
                        DashboardStatsResponse.GroupEnrollment::enrolled).reversed())
                .limit(10)
                .collect(Collectors.toList());
    }

    private DashboardStatsResponse.ChildrenStats getChildrenStats() {
        List<Child> all = childRepository.findAll();
        long active   = all.stream().filter(c -> Boolean.TRUE.equals(c.getActive())).count();
        long inactive = all.size() - active;
        return new DashboardStatsResponse.ChildrenStats(active, inactive, all.size());
    }

    private List<DashboardStatsResponse.TeacherGroups> getTeacherGroups() {
        Optional<Role> teacherRole = roleRepository.findByRoleName("TEACHER");
        if (teacherRole.isEmpty()) return List.of();

        return userRepository.findByRole(teacherRole.get()).stream()
                .map(teacher -> new DashboardStatsResponse.TeacherGroups(
                        teacher.getFirstName() + " " + teacher.getLastName(),
                        groupClassRepository.findByTeacher(teacher).size()
                ))
                .sorted(Comparator.comparingLong(
                        DashboardStatsResponse.TeacherGroups::groupCount).reversed())
                .collect(Collectors.toList());
    }

    private DashboardStatsResponse.WaitlistStats getWaitlistStats() {
        long waiting   = waitlistRepository
                .findByStatusOrderByCreatedAtDesc(WaitlistStatus.WAITING).size();
        long allocated = waitlistRepository
                .findByStatusOrderByCreatedAtDesc(WaitlistStatus.ALLOCATED).size();
        long cancelled = waitlistRepository
                .findByStatusOrderByCreatedAtDesc(WaitlistStatus.CANCELLED).size();
        return new DashboardStatsResponse.WaitlistStats(
                waiting, allocated, cancelled, waiting + allocated + cancelled);
    }
}
