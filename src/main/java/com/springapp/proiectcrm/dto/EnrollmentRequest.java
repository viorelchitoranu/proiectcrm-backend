package com.springapp.proiectcrm.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class EnrollmentRequest {

    // Date părinte
    @NotBlank
    private String parentFirstName;

    @NotBlank
    private String parentLastName;

    @NotBlank
    @Email
    private String parentEmail;

    @NotBlank
    private String parentPhone;

    private String parentAddress;

    @NotBlank
    @Size(min = 6)
    private String parentPassword;

    // 1..4 copii
    @NotNull
    @Valid
    @Size(min = 1, max = 4)
    private List<EnrollmentChildRequest> children;
}
