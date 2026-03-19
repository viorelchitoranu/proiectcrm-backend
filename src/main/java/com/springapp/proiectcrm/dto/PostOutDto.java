package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO de ieșire pentru un mesaj de pe forum.
 *
 * Trimis în două moduri:
 *   1. REST GET /api/board/history/{channel} → lista de postări istorice
 *   2. WebSocket STOMP → broadcast pe /topic/board/{channel} la fiecare post nou
 *
 * Conține datele minime necesare UI-ului pentru a afișa un mesaj:
 *   id, channel, authorId, authorName, authorRole, content, createdAt
 *
 * authorRole — util pentru UI:
 *   ADMIN    → badge albastru „Admin"
 *   TEACHER  → badge verde „Profesor"
 *   PARENT   → fără badge (utilizator normal)
 *
 * GDPR: nu expunem emailul autorului în răspuns — doar numele afișat.
 */
@Data
@AllArgsConstructor
public class PostOutDto {

    private Long   id;
    private String channel;

    // Date autor — extragem din User în service layer
    private Integer authorId;
    private String  authorName;   // "Prenume Nume"
    private String  authorRole;   // "ADMIN", "TEACHER", "PARENT"

    private String        content;
    private LocalDateTime createdAt;
}
