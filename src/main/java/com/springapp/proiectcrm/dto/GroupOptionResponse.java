package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupOptionResponse {
    // Identificatorul grupei
    private Integer groupId;

    // Label pentru dropdown (ex. "Bricks – Luni 17:00 (Școala X, 3 locuri libere)")
    private String label;

    // Info grupa
    private String courseName;
    private String groupName;
    private String schoolName;
    private String schoolAddress;

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime sessionStartTime;

    // Capacitate si locuri
    private Integer maxCapacity;
    private Long enrolledChildren;
    private Integer remainingSpots;
}
