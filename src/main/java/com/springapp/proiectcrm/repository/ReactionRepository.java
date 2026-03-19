package com.springapp.proiectcrm.repository;

import com.springapp.proiectcrm.model.Post;
import com.springapp.proiectcrm.model.Reaction;
import com.springapp.proiectcrm.model.ReactionType;
import com.springapp.proiectcrm.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository pentru reacțiile posturilor din Message Board.
 *
 * Queries principale:
 *   findByPostAndUser  → verifică dacă un user a reacționat deja (pentru toggle/upsert)
 *   findByPost         → toate reacțiile unui post (pentru agregare în service)
 *   countByPostAndType → numărul de reacții per tip (pentru afișare)
 *   deleteAllByPost    → cascadă la ștergerea postului
 */
@Repository
public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    /**
     * Găsește reacția unui user la un post specific.
     * Folosit pentru logica de toggle:
     *   - dacă există și tipul e același → șterge (toggle off)
     *   - dacă există și tipul e diferit → actualizează (schimbare reacție)
     *   - dacă nu există → creează (prima reacție)
     */
    Optional<Reaction> findByPostAndUser(Post post, User user);

    /**
     * Toate reacțiile unui post — folosit pentru a construi sumarul per tip.
     */
    List<Reaction> findByPost(Post post);

    /**
     * Numărul de reacții per tip pentru un post.
     * Returnează Map<ReactionType, Long> construit în service din lista de reacții.
     */
    long countByPostAndType(Post post, ReactionType type);

    /**
     * Șterge toate reacțiile unui post — apelat la ștergerea postului.
     */
    void deleteAllByPost(Post post);
}
