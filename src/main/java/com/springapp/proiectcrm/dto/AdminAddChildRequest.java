package com.springapp.proiectcrm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Modul 2 — Adăugare copil la un părinte existent (admin).
 * Folosit la POST /api/admin/parents/{parentId}/children
 */
@Data
public class AdminAddChildRequest {

    @NotBlank(message = "Prenumele copilului este obligatoriu.")
    private String firstName;

    @NotBlank(message = "Numele de familie al copilului este obligatoriu.")
    private String lastName;

    @Min(value = 1, message = "Vârsta minimă este 1 an.")
    @Max(value = 18, message = "Vârsta maximă este 18 ani.")
    private Integer age;

    private String school;

    private String schoolClass;
}