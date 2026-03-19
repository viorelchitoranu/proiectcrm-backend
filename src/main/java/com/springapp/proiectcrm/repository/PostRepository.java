package com.springapp.proiectcrm.repository;

import com.springapp.proiectcrm.model.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;

import java.util.List;

/**
 * Repository pentru postările de pe forum.
 *
 * Query principal: findRecentByChannel
 *   Returnează ultimele N postări dintr-un canal, în ordine cronologică
 *   (cele mai vechi primele — UI-ul le afișează de sus în jos ca un chat).
 *
 *   ATENTIE la ordinea sortării:
 *     Subquery-ul sortează DESC pentru a lua ultimele N.
 *     Query-ul exterior re-sortează ASC pentru afișare corectă în UI.
 *     Fără subquery, ORDER BY + LIMIT ar returna primele N (cele mai vechi),
 *     nu ultimele N.
 *
 * JOIN FETCH author — evităm N+1 queries la construirea PostOutDto.
 *   Fără JOIN FETCH, pentru fiecare Post s-ar face un query separat pentru User.
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {


    /**
     * Returnează ultimele {limit} postări din canal, sortate DESC (newest first).
     * Inversarea la ASC pentru UI se face în service după fetch.
     * Folosim Pageable în loc de subquery cu LIMIT — compatibil cu toate
     * versiunile MySQL (subquery + LIMIT nu e suportat pe MySQL vechi).
     */
    @Query("""
            SELECT p FROM Post p
            JOIN FETCH p.author
            WHERE p.channel = :channel
            ORDER BY p.createdAt DESC
            """)
    List<Post> findRecentByChannel(@Param("channel") String channel, Pageable pageable);

    void deleteAllByChannel(String channel);
}
