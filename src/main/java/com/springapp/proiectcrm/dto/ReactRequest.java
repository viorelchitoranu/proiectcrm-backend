package com.springapp.proiectcrm.dto;

import com.springapp.proiectcrm.model.ReactionType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cerere de reacție la un post.
 * POST /api/board/posts/{id}/react
 * Disponibil pentru orice utilizator autentificat.
 *
 * Logică toggle (implementată în service):
 *   - Dacă tipul e același cu reacția existentă → șterge reacția (toggle off)
 *   - Dacă tipul e diferit → înlocuiește reacția existentă
 *   - Dacă nu există nicio reacție → creează reacție nouă
 *
 * Răspuns: ReactionSummaryDto actualizat — UI-ul re-randează butoanele de reacție.
 */
@Data
@NoArgsConstructor
public class ReactRequest {

    @NotNull(message = "Tipul reacției este obligatoriu.")
    private ReactionType type;  // LIKE, HEART, LAUGH, WOW, SAD, CLAP
}
