package com.springapp.proiectcrm.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class GroupCreateRequest {
    private int courseId;
    private int schoolId;
    private int teacherId;

    private String name;

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime sessionStartTime;

    private Integer maxCapacity;
    private Double sessionPrice;

    private Boolean active;

    private Integer maxRecoverySlots;
}
