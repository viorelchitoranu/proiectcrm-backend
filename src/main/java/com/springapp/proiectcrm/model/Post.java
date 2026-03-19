package com.springapp.proiectcrm.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entitate JPA pentru un post din Message Board (versiune actualizată — Modul 5 Facebook).
 *
 * Câmpuri noi față de versiunea inițială:
 *   editedAt       → null dacă nu a fost editat; non-null = timestamp ultima editare.
 *                    Editarea este permisă DOAR adminului.
 *   attachmentPath → calea relativă a fișierului atașat, ex: "board/2026/03/doc.pdf"
 *                    null dacă nu există atașament. Atașamentele pot fi adăugate
 *                    DOAR de admin (JPEG, PNG, PDF, max 10MB).
 *   attachmentType → "IMAGE" sau "PDF" — controlează modul de afișare în UI.
 *                    IMAGE → afișat inline cu <img>.
 *                    PDF   → afișat ca link de download.
 *
 * Canalele disponibile (neschimbate):
 *   GENERAL, ANNOUNCEMENTS, GROUP_{id}
 *
 * Reguli de postare (neschimbate față de v1):
 *   GENERAL:       orice utilizator autentificat
 *   ANNOUNCEMENTS: doar ADMIN și TEACHER
 *   GROUP_{id}:    membrii activi + ADMIN
 */
@Entity
@Table(name = "post", indexes = {
        @Index(name = "idx_post_channel_date", columnList = "channel, created_at DESC")
})
@NoArgsConstructor
@Getter
@Setter
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "channel", nullable = false, length = 60)
    private String channel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // ── Câmpuri noi ───────────────────────────────────────────────────────────

    // null = niciodată editat; non-null = admin a editat postul
    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    // Calea relativă a fișierului în /uploads/board/ (ex: "board/abc123.pdf")
    // null dacă nu există atașament
    @Column(name = "attachment_path")
    private String attachmentPath;

    // "IMAGE" sau "PDF" — determină cum e afișat în UI
    // null dacă nu există atașament
    @Column(name = "attachment_type", length = 10)
    private String attachmentType;
}
