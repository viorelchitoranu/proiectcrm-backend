package com.springapp.proiectcrm.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO trimis de admin la POST /api/admin/waitlist/{id}/allocate.
 * Conține ID-ul grupei la care adminul dorește să aloce copilul.
 */
@Data
@NoArgsConstructor
public class WaitlistAllocateRequest {

    @NotNull(message = "ID-ul grupei este obligatoriu.")
    private Integer groupId;
}
