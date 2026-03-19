package com.springapp.proiectcrm.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "attendance_archive")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class AttendanceArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_attendance_archive")
    private Long idAttendanceArchive;

    // IDs originale (pentru trasabilitate)
    @Column(name = "original_attendance_id")
    private Integer originalAttendanceId;

    @Column(name = "original_session_id")
    private Integer originalSessionId;

    @Column(name = "original_group_id")
    private Integer originalGroupId;

    // Info group/session (denormalizat)
    @Column(name = "group_name")
    private String groupName;

    @Column(name = "course_name")
    private String courseName;

    @Column(name = "school_name")
    private String schoolName;

    @Column(name = "teacher_name")
    private String teacherName;

    @Column(name = "session_date")
    private LocalDate sessionDate;

    @Column(name = "session_time")
    private LocalTime sessionTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_status")
    private SessionStatus sessionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type")
    private SessionType sessionType;

    // Info copil/parinte
    @Column(name = "child_id")
    private Integer childId;

    @Column(name = "child_first_name")
    private String childFirstName;

    @Column(name = "child_last_name")
    private String childLastName;

    @Column(name = "parent_id")
    private Integer parentId;

    @Column(name = "parent_name")
    private String parentName;

    @Column(name = "parent_email")
    private String parentEmail;

    @Column(name = "parent_phone")
    private String parentPhone;

    // Attendance original
    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false)
    private AttendanceStatus attendanceStatus;

    @Column(name = "nota", columnDefinition = "TEXT")
    private String nota;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_recovery")
    private boolean isRecovery;

    @Column(name = "recovery_for_session_id")
    private Integer recoveryForSessionId;

    // Metadata arhivare
    @Column(name = "archived_at", nullable = false)
    private LocalDateTime archivedAt;
}
