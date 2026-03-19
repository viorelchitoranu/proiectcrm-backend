package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SchoolOptionResponse {
    private int id;
    private String name;
    private String address;

}
