package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@AllArgsConstructor
public class TeacherRecoveryTargetSessionResponse {
    private int sessionId;
    private LocalDate sessionDate;
    private LocalTime time;

    private int groupId;
    private String groupName;
    private String courseName;

    private int teacherId;
    private String teacherName;

    private String schoolName;


    private long recoverySlotsUsed;
    private Integer recoverySlotsMax;
}