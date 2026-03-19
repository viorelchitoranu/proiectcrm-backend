package com.springapp.proiectcrm.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Comentariu la un post din Message Board.
 *
 * Reguli de business:
 *   - Orice utilizator autentificat poate comenta (ADMIN, TEACHER, PARENT).
 *   - Editarea este permisă DOAR adminului (conform cerințelor).
 *   - Ștergerea: autorul poate șterge comentariul propriu; adminul poate șterge orice.
 *   - Nu există comentarii la comentarii (no threading) — simplitate.
 *
 * Câmpul editedAt:
 *   null = comentariul nu a fost editat niciodată.
 *   non-null = afișăm "(editat)" în UI lângă timestamp.
 *
 * Mapare tabelă: comment
 *   ATENTIE: "comment" este cuvânt rezervat în SQL → tabela se numește "post_comment"
 */
@Entity
@Table(name = "post_comment")
@NoArgsConstructor
@Getter
@Setter
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // FK spre post — comentariile se șterg cascadă dacă postul e șters
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // FK spre autorul comentariului
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // null dacă nu a fost editat; non-null = ultima dată editat
    @Column(name = "edited_at")
    private LocalDateTime editedAt;
}
