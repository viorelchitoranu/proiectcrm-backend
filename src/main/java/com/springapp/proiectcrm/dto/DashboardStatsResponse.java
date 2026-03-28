package com.springapp.proiectcrm.dto;

import java.util.List;

/**
 * DTO pentru datele dashboard-ului admin.
 * Returnează toate datele necesare celor 5 grafice într-un singur request.
 */
public record DashboardStatsResponse(

        /** Grafic 1: Prezențe pe ultimele 6 luni */
        List<MonthlyAttendance> attendanceByMonth,

        /** Grafic 2: Înscrieri active per grupă (top 10 grupe) */
        List<GroupEnrollment> enrollmentsByGroup,

        /** Grafic 3: Copii activi vs inactivi */
        ChildrenStats childrenStats,

        /** Grafic 4: Profesori și numărul de grupe */
        List<TeacherGroups> teacherGroups,

        /** Grafic 5: Statistici waitlist */
        WaitlistStats waitlistStats

) {
    /** Prezențe per lună */
    public record MonthlyAttendance(
            String month,       // ex: "Ian 2026"
            long present,       // număr prezențe marcate
            long sessions       // număr sesiuni ținute în luna respectivă
    ) {}

    /** Înscrieri per grupă */
    public record GroupEnrollment(
            String groupName,
            String courseName,
            long enrolled       // copii înscriși activ
    ) {}

    /** Copii activi vs inactivi */
    public record ChildrenStats(
            long active,
            long inactive,
            long total
    ) {}

    /** Profesor și numărul de grupe */
    public record TeacherGroups(
            String teacherName,
            long groupCount
    ) {}

    /** Statistici waitlist */
    public record WaitlistStats(
            long waiting,
            long allocated,
            long cancelled,
            long total
    ) {}
}
