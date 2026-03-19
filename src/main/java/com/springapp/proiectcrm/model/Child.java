package com.springapp.proiectcrm.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "child")
@NoArgsConstructor
@AllArgsConstructor
// FIX: @Data → @Getter @Setter
@Getter
@Setter
public class Child {

    @Id
    @Column(name = "id_child")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idChild;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", nullable = false)
    private User parent;

    @Column(name = "last_name")
    private String childLastName;

    @Column(name = "first_name")
    private String childFirstName;

    @Column(name = "age")
    private Integer age;

    @Column(name = "school")
    private String school;

    @Column(name = "school_class")
    private String schoolClass;

    @Column(name = "created_at")
    private LocalDateTime childCreatedAt;

    // ── Starea individuală a copilului ────────────────────────────────────────
    // Câmp NOU — necesită migrare SQL: ALTER TABLE child ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1
    //
    // Valoare implicită null tratată ca true în logica de business
    // (toți copiii creați înainte de migrare sunt considerați activi).
    // Dupa migrare, DEFAULT 1 garanteaza ca valoarea nu mai este null.
    @Column(name = "active")
    private Boolean active;
}