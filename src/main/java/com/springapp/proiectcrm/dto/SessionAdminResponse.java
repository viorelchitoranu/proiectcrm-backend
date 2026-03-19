package com.springapp.proiectcrm.dto;

import com.springapp.proiectcrm.model.SessionStatus;
import com.springapp.proiectcrm.model.SessionType;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@AllArgsConstructor
public class SessionAdminResponse {
    private int id;
    private String name;
    private LocalDate sessionDate;
    private LocalTime time;
    private SessionStatus sessionStatus;
    private SessionType sessionType;

    private Integer schoolId;
    private String schoolName;

    private Integer teacherId;
    private String teacherName;
}
