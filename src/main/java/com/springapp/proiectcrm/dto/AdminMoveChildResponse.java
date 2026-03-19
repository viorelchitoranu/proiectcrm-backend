package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class AdminMoveChildResponse {
    private Integer childId;
    private Integer fromGroupId;
    private Integer toGroupId;
    private LocalDate effectiveDate;

    private int archivedAttendanceCount;
    private String message;
}
