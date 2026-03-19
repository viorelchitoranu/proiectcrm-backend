package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Sumarul reacțiilor unui post — agregate pe tip.
 *
 * counts: Map cu numărul de reacții per tip.
 *   ex: { "LIKE": 5, "HEART": 2, "LAUGH": 1 }
 *   Tipurile cu 0 reacții NU apar în map (UI verifică cu ?? 0).
 *
 * myReaction: tipul reacției utilizatorului curent.
 *   null dacă utilizatorul nu a reacționat la acest post.
 *   ex: "LIKE" → butonul Like este evidențiat în UI.
 *
 * totalCount: suma tuturor reacțiilor — afișat ca număr total lângă butoane.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReactionSummaryDto {

    private Map<String, Long> counts;      // { "LIKE": 5, "HEART": 2 }
    private String            myReaction;  // null sau "LIKE", "HEART" etc.
    private long              totalCount;  // total reacții
}
