package com.springapp.proiectcrm.dto;

import com.springapp.proiectcrm.model.WaitlistStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * DTO pentru afișarea unui rând din lista de așteptare în panoul admin.
 * Conține toate datele necesare afișării în tabel + istoricul alocării.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistEntryResponse {

    private int id;

    // Date părinte
    private String parentFirstName;
    private String parentLastName;
    private String parentEmail;
    private String parentPhone;

    // Date copil
    private String childFirstName;
    private String childLastName;
    private Integer childAge;
    private String childSchool;
    private String childSchoolClass;

    // Preferințe exprimate la completarea formularului
    private String preferredCourseName;
    private String preferredSchoolName;
    private String notes;

    // Status și dată
    private WaitlistStatus status;
    private LocalDateTime createdAt;

    // Populat doar când status = ALLOCATED
    private LocalDateTime allocatedAt;
    private Integer allocatedGroupId;
    private String allocatedGroupName;
}
