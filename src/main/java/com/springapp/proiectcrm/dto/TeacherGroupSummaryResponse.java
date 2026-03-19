package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@AllArgsConstructor
public class TeacherGroupSummaryResponse {

    private int groupId;
    private String groupName;
    private String courseName;
    private String schoolName;
    private String schoolAddress;

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime sessionStartTime;

    private Boolean active;
    private int enrolledChildrenCount;
    private long sessionsCount;

    private LocalDateTime createdAt;
    private LocalDateTime startConfirmedAt;

    // METRICELE DE PROGRES
    private long totalSessions;       // cate sesiuni sunt planificate pentru grupa
    private long heldSessions;        // cate au fost tinute (status HELD)
    private long cancelledSessions;   // cate au fost anulate
    private int progressPercent;      // (held / total) * 100

    private long activeChildren;

    // "NOT_STARTED", "ONGOING", "FINISHED"
    private String status;


}