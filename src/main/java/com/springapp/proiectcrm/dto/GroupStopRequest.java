package com.springapp.proiectcrm.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class GroupStopRequest {
    private String reason;
    private LocalDate effectiveDate;
}