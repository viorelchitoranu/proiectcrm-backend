package com.springapp.proiectcrm.dto;


import com.springapp.proiectcrm.model.SessionStatus;

import java.time.LocalDate;
import java.time.LocalTime;

public record SessionSummaryResponse(
        int sessionId,
        LocalDate sessionDate,
        LocalTime time,
        SessionStatus sessionStatus,
        String schoolName,
        String schoolAddress,

        boolean cancellable,
        boolean recoveryRequestAllowed,


        String childAttendanceStatus,
        Integer assignedToSessionId,
        boolean isRecoveryAttendance
) {
}
