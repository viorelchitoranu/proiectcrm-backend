package com.springapp.proiectcrm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pentru cererea de publicare a unui mesaj pe forum.
 *
 * Primit prin WebSocket STOMP la endpoint-ul /app/board/publish.
 * Validat cu @Valid în MessageBoardController.
 *
 * channel — identificatorul canalului:
 *   "GENERAL"       → chat general, toți utilizatorii
 *   "ANNOUNCEMENTS" → anunțuri, scriere restricționată la ADMIN/TEACHER
 *   "GROUP_5"       → grupul cu idGroup=5, acces restricționat la membri
 *
 * content — mesajul propriu-zis.
 *   Max 2000 caractere — suficient pentru anunțuri detaliate.
 *   Min 1 caracter — previne postările goale.
 */
@Data
@NoArgsConstructor
public class PostPublishRequest {

    @NotBlank(message = "Canalul este obligatoriu.")
    private String channel;

    @NotBlank(message = "Conținutul mesajului nu poate fi gol.")
    @Size(max = 2000, message = "Mesajul nu poate depăși 2000 de caractere.")
    private String content;
}
