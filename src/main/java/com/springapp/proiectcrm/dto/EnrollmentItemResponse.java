package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EnrollmentItemResponse {
    private Integer childId;
    private Integer groupId;
    private String groupName;
    private String childFirstName;
    private String childLastName;
}
