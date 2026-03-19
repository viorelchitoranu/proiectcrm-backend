package com.springapp.proiectcrm.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance")
@NoArgsConstructor
@AllArgsConstructor
// FIX: @Data → @Getter @Setter pentru a evita LazyInitializationException
// @Data generează equals/hashCode care pot trigera lazy loading neașteptat
@Getter
@Setter
public class Attendance {

    @Id
    @Column(name = "id_attendance")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idAttendance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", nullable = false)
    private Child child;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttendanceStatus status;

    @Column(name = "nota")
    private String nota;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_recovery")
    private boolean isRecovery;

    @Column(name = "recovery_for_session_id")
    private Integer recoveryForSessionId;

    @Column(name = "assigned_to_session_id")
    private Integer assignedToSessionId;
}
