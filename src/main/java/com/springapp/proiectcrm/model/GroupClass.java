package com.springapp.proiectcrm.model;

import jakarta.persistence.*;
import lombok.*;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;


@Entity
@Table(name = "group_class")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class GroupClass {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_group")
    private int idGroup;// [pk]


    @Column(name = "name")
    private String groupName;

    @Column(name = "start_date")
    private LocalDate groupStartDate;

    @Column(name = "end_date")
    private LocalDate groupEndDate;

    @Column(name = "session_start_time")
    private LocalTime sessionStartTime;

    @Column(name = "max_capacity")
    private int groupMaxCapacity;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "session_price")
    private Double sessionPrice;

    @Column(name = "total_price")
    private Double totalPrice;

    @Column(name = "created_at")
    private LocalDateTime groupCreatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private School school;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id")
    private User teacher;

    @Column(name = "max_recovery_slots")
    private Integer maxRecoverySlots;

    // NOU: când a fost efectiv „pornită” grupa (buton START)
    @Column(name = "start_confirmed_at")
    private LocalDateTime startConfirmedAt;

    @Column(name = "force_stop_at")
    private LocalDateTime forceStopAt;


}
