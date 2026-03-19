package com.springapp.proiectcrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cerere de editare a unui post existent.
 * PUT /api/board/posts/{id}
 * Disponibil DOAR pentru ADMIN — verificat cu @PreAuthorize în controller.
 */
@Data
@NoArgsConstructor
public class EditPostRequest {

    @NotBlank(message = "Conținutul nu poate fi gol.")
    @Size(max = 2000, message = "Postul nu poate depăși 2000 de caractere.")
    private String content;
}
