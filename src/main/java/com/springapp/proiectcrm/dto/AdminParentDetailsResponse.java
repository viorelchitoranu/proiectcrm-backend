package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AdminParentDetailsResponse {
    private AdminParentSummaryResponse parent;
    private List<AdminChildRowResponse> children;
}
