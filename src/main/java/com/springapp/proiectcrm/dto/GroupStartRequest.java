package com.springapp.proiectcrm.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class GroupStartRequest {
    // dacă adminul vrea sa aleaga manual data de la care condidera ca grupa a pornit
    private LocalDate effectiveStartDate;
}
