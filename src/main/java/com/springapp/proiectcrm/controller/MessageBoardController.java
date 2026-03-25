package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.dto.*;
import com.springapp.proiectcrm.service.MessageBoardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

/**
 * Controller Message Board — versiunea Facebook.
 *
 * ── WebSocket STOMP ──────────────────────────────────────────────────────
 *   /app/board/publish   → publicare post nou
 *
 * ── REST endpoints ──────────────────────────────────────────────────────
 *   GET    /api/board/feed/{channel}              → feed cu carduri complete
 *   POST   /api/board/posts/{id}/comments         → adăugare comentariu
 *   POST   /api/board/posts/{id}/react            → reacție (toggle/upsert)
 *   PUT    /api/board/posts/{id}                  → editare post [ADMIN]
 *   POST   /api/board/posts/{id}/attachment       → atașare fișier [ADMIN]
 *   DELETE /api/board/posts/{id}                  → ștergere post [ADMIN]
 *
 * ── WebSocket topics (subscribe) ────────────────────────────────────────
 *   /topic/board/{channel}            → posturi noi + posturi editate
 *   /topic/board/{channel}/comments   → comentarii noi
 *   /topic/board/{channel}/reactions  → reacții actualizate
 */
@RestController
@RequiredArgsConstructor
public class MessageBoardController {

    private final MessageBoardService messageBoardService;

    // ── WebSocket STOMP ───────────────────────────────────────────────────────

    @MessageMapping("/board/publish")
    public void publish(@Valid @Payload PostPublishRequest request, Principal principal) {
        messageBoardService.publishPost(request, principal);
    }

    // ── REST: Feed ────────────────────────────────────────────────────────────

    /**
     * Returnează ultimele {limit} posturi dintr-un canal ca PostCardDto-uri complete.
     * Apelat la încărcarea inițială a paginii de forum.
     */
    @GetMapping("/api/board/feed/{channel}")
    @ResponseBody
    public ResponseEntity<List<PostCardDto>> getFeed(
            @PathVariable String channel,
            @RequestParam(defaultValue = "50") int limit,
            Principal principal) {
        return ResponseEntity.ok(messageBoardService.getFeed(channel, limit, principal));
    }

    // ── REST: Comentarii ──────────────────────────────────────────────────────

    /**
     * Adaugă un comentariu la un post.
     * Disponibil pentru orice utilizator autentificat.
     */
    @PostMapping("/api/board/posts/{id}/comments")
    @ResponseBody
    public ResponseEntity<CommentDto> addComment(
            @PathVariable Long id,
            @Valid @RequestBody AddCommentRequest request,
            Principal principal) {
        return ResponseEntity.status(201)
                .body(messageBoardService.addComment(id, request, principal));
    }

    // ── REST: Reacții ─────────────────────────────────────────────────────────

    /**
     * Toggle/upsert reacție la un post.
     * Returnează sumarul actualizat al reacțiilor.
     */
    @PostMapping("/api/board/posts/{id}/react")
    @ResponseBody
    public ResponseEntity<ReactionSummaryDto> react(
            @PathVariable Long id,
            @Valid @RequestBody ReactRequest request,
            Principal principal) {
        return ResponseEntity.ok(messageBoardService.react(id, request, principal));
    }

    // ── REST: Editare post (admin only) ───────────────────────────────────────

    @PutMapping("/api/board/posts/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PostCardDto> editPost(
            @PathVariable Long id,
            @Valid @RequestBody EditPostRequest request,
            Principal principal) {
        return ResponseEntity.ok(messageBoardService.editPost(id, request, principal));
    }

    // ── REST: Atașament (admin only) ──────────────────────────────────────────

    /**
     * Adaugă sau înlocuiește atașamentul unui post.
     * Acceptă JPEG, PNG, PDF — max 10MB.
     * multipart/form-data cu câmpul "file".
     */
    @PostMapping(value = "/api/board/posts/{id}/attachment",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PostCardDto> uploadAttachment(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            Principal principal) {
        return ResponseEntity.ok(messageBoardService.uploadAttachment(id, file, principal));
    }

    // ── REST: Ștergere post (admin only) ──────────────────────────────────────

    @DeleteMapping("/api/board/posts/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deletePost(@PathVariable Long id) {
        messageBoardService.deletePost(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Șterge un comentariu (ADMIN only).
     * DELETE /api/board/comments/{id}
     */
    @DeleteMapping("/api/board/comments/{id}")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id) {
        messageBoardService.deleteComment(id);
        return ResponseEntity.noContent().build();
    }

}
