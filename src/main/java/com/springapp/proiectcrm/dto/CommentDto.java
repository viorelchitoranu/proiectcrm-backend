package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO de ieșire pentru un comentariu al unui post din Message Board.
 *
 * Trimis în răspunsul REST și prin WebSocket la adăugarea unui comentariu nou.
 *
 * editedAt: null = niciodată editat; non-null = afișăm "(editat)" în UI lângă timestamp.
 * authorRole: "ADMIN", "TEACHER", "PARENT" — folosit pentru badge-uri în UI.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentDto {

    private Long          id;
    private Long          postId;
    private Integer       authorId;
    private String        authorName;    // "Prenume Nume"
    private String        authorRole;    // "ADMIN", "TEACHER", "PARENT"
    private String        content;
    private LocalDateTime createdAt;
    private LocalDateTime editedAt;      // null dacă nu a fost editat
}
