package com.springapp.proiectcrm.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Reacția unui utilizator la un post din Message Board.
 *
 * Reguli de business:
 *   - Un utilizator poate da O SINGURĂ reacție per post.
 *     Dacă apasă altă reacție, cea veche se înlocuiește (upsert).
 *     Dacă apasă aceeași reacție de două ori, reacția se șterge (toggle off).
 *   - Orice utilizator autentificat poate reacționa (ADMIN, TEACHER, PARENT).
 *
 * Constraint BD:
 *   UNIQUE(post_id, user_id) — garantat la nivel de BD, nu doar aplicație.
 *   Previne race conditions când doi clienți trimit reacții simultan.
 *
 * Mapare tabelă: reaction
 */
@Entity
@Table(
        name = "reaction",
        uniqueConstraints = {
                // OBLIGATORIU: un user poate da o singură reacție per post
                @UniqueConstraint(name = "uq_reaction_post_user",
                        columnNames = {"post_id", "user_id"})
        }
)
@NoArgsConstructor
@Getter
@Setter
public class Reaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // FK spre post — reacțiile se șterg cascadă dacă postul e șters
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // FK spre user — autorul reacției
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private ReactionType type;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
