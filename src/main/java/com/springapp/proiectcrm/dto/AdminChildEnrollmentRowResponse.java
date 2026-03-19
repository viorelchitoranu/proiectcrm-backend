package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@AllArgsConstructor
public class AdminChildEnrollmentRowResponse {
    private Integer groupId;
    private String groupName;
    private String courseName;
    private String schoolName;
    private LocalDate enrollmentDate;
    private LocalDate groupStartDate;
    private LocalDate groupEndDate;
    private LocalTime sessionStartTime;
    private Boolean active; // enrollment activ sau nu
}
