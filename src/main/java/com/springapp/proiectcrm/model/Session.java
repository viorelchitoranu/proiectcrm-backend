package com.springapp.proiectcrm.model;

import jakarta.persistence.*;
import lombok.*;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "session")
@NoArgsConstructor
@Getter
@Setter
@AllArgsConstructor
public class Session {

    @Id
    @Column(name = "id_session")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int idSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="group_id", nullable=false)
    private GroupClass group;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name="school_id")
    private School school;


    @Column(name = "name")
    private String name;

    @Column(name = "session_date", nullable=false)
    private LocalDate sessionDate;

    @Column(name = "time", nullable=false)
    private LocalTime time;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", nullable = false, length = 32)
    private SessionStatus sessionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 16)
    private SessionType sessionType;

    @Column(name = "created_at")
    private LocalDateTime sessionCreatedAt;

    @OneToMany(mappedBy = "session")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<TeacherSession> teacherAssigment;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name="attendance_taken_at")
    private LocalDateTime attendanceTakenAt;

}
