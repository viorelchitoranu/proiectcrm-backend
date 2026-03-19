package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

/**
 * Serviciu pentru Message Board — versiunea Facebook cu carduri, comentarii,
 * reacții, editare și atașamente.
 *
 * Metode noi față de versiunea inițială:
 *   getFeed()          → returnează PostCardDto-uri complete (cu comentarii + reacții)
 *   addComment()       → adaugă comentariu și face broadcast WebSocket
 *   react()            → toggle/upsert reacție și face broadcast WebSocket
 *   editPost()         → editare post de admin și face broadcast WebSocket
 *   uploadAttachment() → adaugă fișier la post (admin) și face broadcast WebSocket
 *
 * getHistory() este păstrat pentru compatibilitate cu codul existent.
 */
public interface MessageBoardService {

    // ── Publicare post ────────────────────────────────────────────────────────
    PostOutDto publishPost(PostPublishRequest request, Principal principal);

    // ── Feed (nou) ────────────────────────────────────────────────────────────
    List<PostCardDto> getFeed(String channel, int limit, Principal principal);

    // ── History (backward compatibility) ─────────────────────────────────────
    List<PostOutDto> getHistory(String channel, int limit, Principal principal);

    // ── Comentarii ────────────────────────────────────────────────────────────
    CommentDto addComment(Long postId, AddCommentRequest request, Principal principal);

    // ── Reacții ───────────────────────────────────────────────────────────────
    ReactionSummaryDto react(Long postId, ReactRequest request, Principal principal);

    // ── Editare post (admin only) ─────────────────────────────────────────────
    PostCardDto editPost(Long postId, EditPostRequest request, Principal principal);

    // ── Atașament (admin only) ────────────────────────────────────────────────
    PostCardDto uploadAttachment(Long postId, MultipartFile file, Principal principal);

    // ── Ștergere post (admin only) ────────────────────────────────────────────
    void deletePost(Long postId);

    void deleteComment(Long commentId);
}
