package com.springapp.proiectcrm.model;

/**
 * Tipurile de reacții disponibile pe un post din Message Board.
 *
 * Mapare emoji → tip:
 *   LIKE  → 👍
 *   HEART → ❤️
 *   LAUGH → 😂
 *   WOW   → 😮
 *   SAD   → 😢
 *   CLAP  → 👏
 *
 * Stocat ca VARCHAR în BD (EnumType.STRING) — mai lizibil decât ordinal
 * și rezistent la reordonarea enum-ului.
 */
public enum ReactionType {
    LIKE,
    HEART,
    LAUGH,
    WOW,
    SAD,
    CLAP
}
