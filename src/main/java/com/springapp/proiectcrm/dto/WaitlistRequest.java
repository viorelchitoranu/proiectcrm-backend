package com.springapp.proiectcrm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pentru cererea publică de înscriere pe lista de așteptare.
 *
 * Trimis de frontend la POST /api/public/waitlist.
 * Nu necesită autentificare — formular public accesibil oricui.
 *
 * Câmpurile de preferință (preferredCourseName, preferredSchoolName) sunt
 * trimise ca stringuri din frontend (numele afișat), nu ca ID-uri.
 * Adminul vede aceste preferințe ca informație orientativă la alocare.
 */
@Data
@NoArgsConstructor
public class WaitlistRequest {

    // ── Date părinte ──────────────────────────────────────────────────────────

    @NotBlank(message = "Prenumele părintelui este obligatoriu.")
    private String parentFirstName;

    @NotBlank(message = "Numele părintelui este obligatoriu.")
    private String parentLastName;

    @NotBlank(message = "Emailul este obligatoriu.")
    @Email(message = "Emailul nu are un format valid.")
    private String parentEmail;

    @NotBlank(message = "Numărul de telefon este obligatoriu.")
    private String parentPhone;

    private String parentAddress;

    // ── Date copil ────────────────────────────────────────────────────────────

    @NotBlank(message = "Prenumele copilului este obligatoriu.")
    private String childFirstName;

    @NotBlank(message = "Numele copilului este obligatoriu.")
    private String childLastName;

    @NotNull(message = "Vârsta copilului este obligatorie.")
    private Integer childAge;

    private String childSchool;
    private String childSchoolClass;

    // ── Preferințe ────────────────────────────────────────────────────────────
    // Trimise ca stringuri (ex: "Robotică", "Școala Gimnazială nr. 5")
    // Sunt informative — adminul decide grupa finală la alocare

    private String preferredCourseName;
    private String preferredSchoolName;

    // Mesaj opțional din partea părintelui
    private String notes;
}
