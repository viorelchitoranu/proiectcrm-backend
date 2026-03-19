package com.springapp.proiectcrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ParentPasswordUpdateRequest {
    @NotBlank
    @Size(min = 6, max = 20)
    private String newPassword;
}
