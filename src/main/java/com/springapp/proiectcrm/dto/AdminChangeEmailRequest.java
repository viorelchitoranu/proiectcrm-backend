package com.springapp.proiectcrm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Modul 3 — Schimbare email părinte (admin).
 * Folosit la PATCH /api/admin/parents/{parentId}/email
 */
@Data
public class AdminChangeEmailRequest {

    @NotBlank(message = "Noul email este obligatoriu.")
    @Email(message = "Formatul email-ului este invalid.")
    private String newEmail;
}
