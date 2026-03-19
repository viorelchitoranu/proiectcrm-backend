package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@AllArgsConstructor
public class ChildEnrollmentResponse {
    private int groupId;
    private String courseName;
    private String groupName;
    private String schoolName;
    private String schoolAddress;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime sessionStartTime;
    private String status;  //ex. "ONGOING" / "FINISHED"

}
