package com.springapp.proiectcrm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EnrollmentChildRequest {

    @NotBlank
    private String childFirstName;

    @NotBlank
    private String childLastName;

    @NotNull
    @Min(1)
    @Max(18)
    private Integer childAge;

    private String childSchool;
    private String childSchoolClass;

    @NotNull
    private Integer groupId;
}