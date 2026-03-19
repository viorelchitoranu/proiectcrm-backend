package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO complet pentru un post în stilul card Facebook.
 *
 * Conține tot ce are nevoie UI-ul pentru a randa un card complet:
 *   - datele postului (autor, conținut, timestamps)
 *   - atașamentul opțional (imagine sau PDF)
 *   - lista de comentarii (toate, ordonate cronologic)
 *   - sumarul reacțiilor (agregate + reacția utilizatorului curent)
 *
 * Trimis prin:
 *   1. REST GET /api/board/feed/{channel}       → lista inițială de carduri
 *   2. WebSocket /topic/board/{channel}          → post nou publicat
 *   3. WebSocket /topic/board/{channel}/updates  → post actualizat (reacție/comentariu)
 *
 * attachmentUrl: calea accesibilă din browser, ex: "/uploads/board/abc.pdf"
 *   null dacă nu există atașament.
 * attachmentType: "IMAGE" sau "PDF"
 *   IMAGE → UI afișează cu <img> inline
 *   PDF   → UI afișează ca buton/link de download
 *
 * editedAt: null dacă postul nu a fost editat de admin.
 *   non-null → UI afișează "(editat)" lângă timestamp.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostCardDto {

    private Long                id;
    private String              channel;
    private Integer             authorId;
    private String              authorName;     // "Prenume Nume"
    private String              authorRole;     // "ADMIN", "TEACHER", "PARENT"
    private String              content;
    private LocalDateTime       createdAt;
    private LocalDateTime       editedAt;       // null dacă needitat
    private String              attachmentUrl;  // null dacă fără atașament
    private String              attachmentType; // "IMAGE", "PDF" sau null
    private List<CommentDto>    comments;
    private ReactionSummaryDto  reactions;
}
