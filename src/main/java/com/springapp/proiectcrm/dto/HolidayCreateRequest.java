package com.springapp.proiectcrm.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class HolidayCreateRequest {

    private LocalDate date;
    private String description;

}
