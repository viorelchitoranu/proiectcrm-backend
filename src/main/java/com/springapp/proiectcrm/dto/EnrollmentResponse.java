package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class EnrollmentResponse {

    private Integer parentId;
    private List<EnrollmentItemResponse> enrollments;
    private String message;

}
