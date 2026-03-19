package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChildSummaryResponse {
    private int childId;
    private String firstName;
    private String lastName;
    private Integer age;
    private String school;
    private String schoolClass;


}
