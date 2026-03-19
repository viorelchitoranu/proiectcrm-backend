package com.springapp.proiectcrm.dto;

import com.springapp.proiectcrm.model.ReactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cerere de adăugare comentariu la un post.
 * POST /api/board/posts/{id}/comments
 * Disponibil pentru orice utilizator autentificat.
 */
@Data
@NoArgsConstructor
public class AddCommentRequest {

    @NotBlank(message = "Conținutul comentariului nu poate fi gol.")
    @Size(max = 1000, message = "Comentariul nu poate depăși 1000 de caractere.")
    private String content;
}
