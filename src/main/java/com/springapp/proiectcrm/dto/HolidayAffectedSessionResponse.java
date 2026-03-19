package com.springapp.proiectcrm.dto;

import com.springapp.proiectcrm.model.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@AllArgsConstructor
public class HolidayAffectedSessionResponse {

    private int sessionId;
    private LocalDate sessionDate;
    private LocalTime time;
    private SessionStatus status;

    private Integer groupId;
    private String groupName;
    private String courseName;
    private String schoolName;
    private String teacherName;
}