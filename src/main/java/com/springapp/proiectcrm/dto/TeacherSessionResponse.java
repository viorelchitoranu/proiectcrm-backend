package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@AllArgsConstructor
public class TeacherSessionResponse {

    private int sessionId;
    private LocalDate sessionDate;
    private LocalTime time;
    private String status;
    private String type;
}
