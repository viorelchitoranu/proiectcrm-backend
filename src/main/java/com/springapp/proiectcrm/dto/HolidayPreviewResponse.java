package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
public class HolidayPreviewResponse {
    private LocalDate startDate;
    private LocalDate endDate;

    private int willCancelCount;
    private int blockedCount;

    private List<HolidayAffectedSessionResponse> willCancel;
    private List<HolidayBlockedSessionResponse> blocked;
}