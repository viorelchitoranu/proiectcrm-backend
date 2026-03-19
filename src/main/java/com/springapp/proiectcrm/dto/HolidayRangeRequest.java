package com.springapp.proiectcrm.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class HolidayRangeRequest {
    private LocalDate startDate;
    private LocalDate endDate;
    private String description;
}