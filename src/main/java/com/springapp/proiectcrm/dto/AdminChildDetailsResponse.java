package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AdminChildDetailsResponse {
    private AdminChildRowResponse child;
    private AdminParentSummaryResponse parent;
   // enrollments
    private List<AdminChildEnrollmentRowResponse> enrollments;
}
