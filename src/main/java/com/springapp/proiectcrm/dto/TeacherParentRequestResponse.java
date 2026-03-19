package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@AllArgsConstructor
public class TeacherParentRequestResponse {

    private int attendanceId;
    private int childId;
    private String childName;

    private String parentName;
    private String parentPhone;
    private String parentEmail;

    private int sessionId;
    private LocalDate sessionDate;
    private LocalTime sessionTime;

    private String groupName;
    private String courseName;
    private String schoolName;

    // "CANCELLED_BY_PARENT" / "RECOVERY_REQUESTED"
    private String type;


    private String note;

    private int groupId;

    // derivat din nota (ASSIGNED_TO_SESSION=123) sau null
    private Integer assignedToSessionId;

    private String status;  // status real: CANCELLED_BY_PARENT / RECOVERY_REQUESTED / RECOVERY_BOOKED
}
