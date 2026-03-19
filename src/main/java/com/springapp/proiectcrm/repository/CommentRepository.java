package com.springapp.proiectcrm.repository;

import com.springapp.proiectcrm.model.Comment;
import com.springapp.proiectcrm.model.Post;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository pentru comentariile posturilor din Message Board.
 *
 * findByPostOrderByCreatedAtAsc — returnează comentariile unui post în ordine
 * cronologică (primele comentarii primele, ca pe Facebook).
 * JOIN FETCH author — evităm N+1 queries la construirea CommentDto.
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * Toate comentariile unui post, sortate cronologic ASC.
     * JOIN FETCH pe author pentru a evita N+1 queries.
     */
    @Query("""
            SELECT c FROM Comment c
            JOIN FETCH c.author
            WHERE c.post = :post
            ORDER BY c.createdAt ASC
            """)
    List<Comment> findByPostOrderByCreatedAtAsc(@Param("post") Post post);

    /**
     * Numărul de comentarii per post — folosit în PostCardDto pentru afișarea
     * contorului "X comentarii" fără a încărca toate comentariile.
     */
    long countByPost(Post post);

    /**
     * Șterge toate comentariile unui post — apelat din MessageBoardService.deletePost()
     * înainte de ștergerea postului (cascadă manuală, mai sigur decât cascade=ALL).
     */
    void deleteAllByPost(Post post);
}
