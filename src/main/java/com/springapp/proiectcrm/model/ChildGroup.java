package com.springapp.proiectcrm.model;

import jakarta.persistence.*;
import lombok.*;


import java.time.LocalDate;



@Entity
@Table(name = "child_group")
@NoArgsConstructor
@Getter
@Setter
@AllArgsConstructor
public class ChildGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id_child_group")
    private int idChildGroup;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="child_id", nullable=false)
    private Child child;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="group_id", nullable=false)
    private GroupClass group;

    @Column(name = "enrollment_date")
    private LocalDate enrollmentDate;

    @Column(name = "active")
    private Boolean active;
}
