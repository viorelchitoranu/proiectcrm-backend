package com.springapp.proiectcrm.dto;

import com.springapp.proiectcrm.model.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@AllArgsConstructor
public class TeacherSessionSummaryResponse {

    private int sessionId;
    private LocalDate sessionDate;
    private LocalTime time;
    private SessionStatus status;

    private String schoolName;
    private String schoolAddress;

    private boolean attendanceTaken;   // true daca existăa cel putin un Attendance pentru sesiune

    private long recoverySlotsUsed;
    private Integer recoverySlotsMax;
}
