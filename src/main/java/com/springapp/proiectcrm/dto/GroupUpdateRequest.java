package com.springapp.proiectcrm.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class GroupUpdateRequest {

    private Integer teacherId;
    private Integer schoolId;
    private LocalDate endDate;
    private Boolean active;

    //compuri pentru edit din AdminDashboard
    private String name;               // groupName
    private Integer maxCapacity;       // groupMaxCapacity
    private Integer maxRecoverySlots;  // maxRecoverySlots
    private Double sessionPrice;       // sessionPrice
}
