package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@AllArgsConstructor
public class ChildGroupScheduleResponse {
    // info copil
    private Integer childId;
    private String childFirstName;
    private String childLastName;

    // info grupa
    private Integer groupId;
    private String groupName;
    private String courseName;
    private String schoolName;
    private String schoolAddress;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime sessionStartTime;

    private String status;
    // lista tuturor sesiunilor
    private List<SessionSummaryResponse> sessions;
}
