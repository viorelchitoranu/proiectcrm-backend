package com.springapp.proiectcrm.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;


@Entity
@Table(name = "teacher_session")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class TeacherSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_teacher_session")
    private int idTeacherSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name ="session_id", nullable = false)
    private Session session;

    @Enumerated(EnumType.STRING)
    @Column(name = "teaching_role", nullable = false)
    private TeachingRole teachingRole;


    @Column(name="created_at")
    private LocalDateTime createdAt;



}
