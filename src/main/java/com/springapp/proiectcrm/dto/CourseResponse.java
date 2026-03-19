package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class CourseResponse {
    private int id;
    private String name;
    private LocalDate createdAt;
}

