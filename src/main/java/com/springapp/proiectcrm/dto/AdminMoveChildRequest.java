package com.springapp.proiectcrm.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class AdminMoveChildRequest {
    private Integer fromGroupId;
    private Integer toGroupId;
    private LocalDate effectiveDate;
}