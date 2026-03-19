package com.springapp.proiectcrm.dto;

import lombok.Data;

@Data
public class TeacherAllocateRecoveryRequest {

    // sesiunea aleasa de profesor pentru recuperare
    private int targetSessionId;

    private Integer assignedToSessionId;
}
