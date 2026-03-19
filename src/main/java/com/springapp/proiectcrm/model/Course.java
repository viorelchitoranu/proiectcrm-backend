package com.springapp.proiectcrm.model;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Date;
import java.time.LocalDate;

import java.util.List;


@Entity
@Table(name = "course")
@NoArgsConstructor
@Getter
@Setter
@AllArgsConstructor
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_course")
    private int idCourse;

    @Column(name = "name")
    private String name;

    @Column(name = "created_at")
    private LocalDate courseCreatedAt;

    @OneToMany(mappedBy = "course")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @com.fasterxml.jackson.annotation.JsonIgnore
    private List<GroupClass> groupClasses;

}
