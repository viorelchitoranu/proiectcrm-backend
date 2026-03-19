package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminParentSummaryResponse {
    private Integer parentId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private Long childrenCount;
    private Boolean active;
}