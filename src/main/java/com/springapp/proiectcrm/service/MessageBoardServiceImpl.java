package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.*;
import com.springapp.proiectcrm.exception.BusinessException;
import com.springapp.proiectcrm.exception.ErrorCode;
import com.springapp.proiectcrm.model.*;
import com.springapp.proiectcrm.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementare serviciu Message Board — versiunea Facebook cu carduri, comentarii,
 * reacții, editare și atașamente.
 *
 * ──────────────────────────────────────────────────────────────────────────
 * Flux publicare post:
 *   publishPost() → verificare permisiuni → salvare Post → construire PostCardDto
 *   → broadcast WebSocket pe /topic/board/{channel}
 *
 * Flux comentariu nou:
 *   addComment() → salvare Comment → construire CommentDto
 *   → broadcast WebSocket pe /topic/board/{channel}/comments
 *   (frontend adaugă comentariul la cardul corespunzător după postId)
 *
 * Flux reacție:
 *   react() → toggle/upsert Reaction → construire ReactionSummaryDto
 *   → broadcast WebSocket pe /topic/board/{channel}/reactions
 *
 * Flux editare post (admin):
 *   editPost() → actualizare content + editedAt → broadcast PostCardDto actualizat
 *
 * Flux atașament (admin):
 *   uploadAttachment() → FileStorageService.store() → actualizare Post
 *   → broadcast PostCardDto actualizat cu attachmentUrl
 *
 * Broadcast WebSocket:
 *   Există 3 topicuri per canal pentru a minimiza datele trimise:
 *   /topic/board/{channel}            → post nou (PostCardDto complet)
 *   /topic/board/{channel}/comments   → comentariu nou (CommentDto)
 *   /topic/board/{channel}/reactions  → reacție actualizată (Map cu postId + summary)
 *
 * Permisiuni postare:
 *   GENERAL:       orice utilizator autentificat (ADMIN, TEACHER, PARENT)
 *   ANNOUNCEMENTS: doar ADMIN și TEACHER
 *   GROUP_{id}:    membrii activi ai grupei + ADMIN
 *
 * Permisiuni editare/atașamente:
 *   Doar ADMIN poate edita orice post sau adăuga atașamente.
 * ──────────────────────────────────────────────────────────────────────────
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageBoardServiceImpl implements MessageBoardService {

    private final PostRepository             postRepository;
    private final CommentRepository          commentRepository;
    private final ReactionRepository         reactionRepository;
    private final UserRepository             userRepository;
    private final GroupClassRepository       groupClassRepository;
    private final ChildGroupRepository       childGroupRepository;
    private final FileStorageService         fileStorageService;
    private final SimpMessageSendingOperations messagingTemplate;

    // Self-injection — necesar pentru ca apelurile între metode @Transactional
// să treacă prin proxy-ul Spring (altfel readOnly=true pe getFeed() e ignorat)
    @Autowired
    @Lazy
    private MessageBoardService self;

    // URL de bază pentru accesul la fișiere din browser
    @Value("${upload.url-prefix:/uploads}")
    private String uploadUrlPrefix;

    private static final String CHANNEL_GENERAL       = "GENERAL";
    private static final String CHANNEL_ANNOUNCEMENTS = "ANNOUNCEMENTS";
    private static final String CHANNEL_GROUP_PREFIX  = "GROUP_";

    private static final int DEFAULT_HISTORY_LIMIT = 50;
    private static final int MAX_HISTORY_LIMIT     = 200;

    // Prefix topic WebSocket — folosit în 5 locuri pentru broadcast
// Exemplu: /topic/board/GENERAL, /topic/board/GROUP_1/comments
    private static final String TOPIC_BOARD_PREFIX = "/topic/board/";

    // ── Publicare post ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public PostOutDto publishPost(PostPublishRequest request, Principal principal) {
        User author  = requireUserByEmail(principal.getName());
        String channel = request.getChannel().trim().toUpperCase();
        checkWritePermission(channel, author);

        Post post = new Post();
        post.setChannel(channel);
        post.setAuthor(author);
        post.setContent(request.getContent().trim());
        post.setCreatedAt(LocalDateTime.now());
        Post saved = postRepository.save(post);

        // Construim PostCardDto complet (fără comentarii și fără reacții — post nou)
        PostCardDto card = toCardDto(saved, author, Collections.emptyList(),
                buildEmptyReactions());
        broadcastPost(channel, card);

        log.info("BOARD_POST channel={} authorId={}", channel, author.getIdUser());
        return toDto(saved, author);
    }

    // ── Feed (înlocuiește getHistory) ─────────────────────────────────────────

    /**
     * Returnează ultimele N posturi dintr-un canal ca PostCardDto-uri complete
     * (fiecare cu comentarii + reacții).
     * Înlocuiește vechea metodă getHistory() care returna PostOutDto simplu.
     */
    @Override
    @Transactional(readOnly = true)
    public List<PostCardDto> getFeed(String channel, int limit, Principal principal) {
        User user = requireUserByEmail(principal.getName());
        String normalizedChannel = channel.trim().toUpperCase();
        int safeLimit = Math.min(limit <= 0 ? DEFAULT_HISTORY_LIMIT : limit, MAX_HISTORY_LIMIT);

        checkReadPermission(normalizedChannel, user);

        List<Post> posts = postRepository.findRecentByChannel(
                normalizedChannel, PageRequest.of(0, safeLimit));

        // Re-sortăm ASC (oldest first) pentru afișare corectă în feed
        return posts.reversed().stream()
                .map(p -> buildFullCard(p, user))
                .toList();
    }

    // ── Adăugare comentariu ───────────────────────────────────────────────────

    /**
     * Adaugă un comentariu la un post.
     * Orice utilizator autentificat poate comenta dacă are acces la canal.
     * Broadcast pe /topic/board/{channel}/comments cu CommentDto.
     */
    @Override
    @Transactional
    public CommentDto addComment(Long postId, AddCommentRequest request, Principal principal) {
        User author = requireUserByEmail(principal.getName());
        Post post   = requirePost(postId);

        // Verificare că utilizatorul are acces la canalul postului
        checkReadPermission(post.getChannel(), author);

        Comment comment = new Comment();
        comment.setPost(post);
        comment.setAuthor(author);
        comment.setContent(request.getContent().trim());
        comment.setCreatedAt(LocalDateTime.now());
        Comment saved = commentRepository.save(comment);

        CommentDto dto = toCommentDto(saved, author);

        // Broadcast comentariu nou pe topicul dedicat comentariilor
        // Frontend adaugă comentariul la cardul cu postId corespunzător
        broadcastComment(post.getChannel(), postId, dto);

        log.info("BOARD_COMMENT postId={} authorId={}", postId, author.getIdUser());
        return dto;
    }

    // ── Reacție ───────────────────────────────────────────────────────────────

    /**
     * Toggle/upsert reacție la un post.
     *
     * Logică:
     *   - Nu există reacție → creează
     *   - Există reacție cu același tip → șterge (toggle off)
     *   - Există reacție cu tip diferit → actualizează tipul
     *
     * Broadcast ReactionSummaryDto actualizat pe /topic/board/{channel}/reactions.
     */
    @Override
    @Transactional
    public ReactionSummaryDto react(Long postId, ReactRequest request, Principal principal) {
        User user = requireUserByEmail(principal.getName());
        Post post = requirePost(postId);

        checkReadPermission(post.getChannel(), user);

        Optional<Reaction> existing = reactionRepository.findByPostAndUser(post, user);

        if (existing.isPresent()) {
            Reaction r = existing.get();
            if (r.getType() == request.getType()) {
                // Același tip → toggle off (șterge reacția)
                reactionRepository.delete(r);
            } else {
                // Tip diferit → actualizează
                r.setType(request.getType());
                r.setCreatedAt(LocalDateTime.now());
                reactionRepository.save(r);
            }
        } else {
            // Nu există → creează
            Reaction r = new Reaction();
            r.setPost(post);
            r.setUser(user);
            r.setType(request.getType());
            r.setCreatedAt(LocalDateTime.now());
            reactionRepository.save(r);
        }

        // Recalculăm sumarul DUPĂ modificare
        ReactionSummaryDto summary = buildReactionSummary(post, user);

        // Broadcast sumar actualizat
        broadcastReactions(post.getChannel(), postId, summary);

        return summary;
    }

    // ── Editare post (admin only) ─────────────────────────────────────────────

    /**
     * Editează conținutul unui post.
     * Doar ADMIN — verificat cu @PreAuthorize în controller.
     * Setează editedAt = now() pentru afișarea marcajului "(editat)" în UI.
     */
    @Override
    @Transactional
    public PostCardDto editPost(Long postId, EditPostRequest request, Principal principal) {
        User admin = requireUserByEmail(principal.getName());
        Post post  = requirePost(postId);

        post.setContent(request.getContent().trim());
        post.setEditedAt(LocalDateTime.now());
        postRepository.save(post);

        PostCardDto card = buildFullCard(post, admin);

        // Broadcast post actualizat — frontend înlocuiește cardul existent
        broadcastPost(post.getChannel(), card);

        log.info("BOARD_EDIT postId={} adminId={}", postId, admin.getIdUser());
        return card;
    }

    // ── Atașament (admin only) ────────────────────────────────────────────────

    /**
     * Adaugă sau înlocuiește atașamentul unui post.
     * Doar ADMIN — verificat cu @PreAuthorize în controller.
     *
     * Dacă postul are deja un atașament, cel vechi este șters de pe disk.
     */
    @Override
    @Transactional
    public PostCardDto uploadAttachment(Long postId, MultipartFile file, Principal principal) {
        User admin = requireUserByEmail(principal.getName());
        Post post  = requirePost(postId);

        // Ștergem atașamentul vechi de pe disk dacă există
        if (post.getAttachmentPath() != null) {
            fileStorageService.delete(post.getAttachmentPath());
        }

        String relativePath    = fileStorageService.store(file);
        String attachmentType  = fileStorageService.resolveAttachmentType(file);

        post.setAttachmentPath(relativePath);
        post.setAttachmentType(attachmentType);
        postRepository.save(post);

        PostCardDto card = buildFullCard(post, admin);
        broadcastPost(post.getChannel(), card);

        log.info("BOARD_ATTACH postId={} path={} type={}", postId, relativePath, attachmentType);
        return card;
    }

    // ── Ștergere post (admin only) ────────────────────────────────────────────

    @Override
    @Transactional
    public void deletePost(Long postId) {
        Post post = requirePost(postId);

        // Ștergem fișierul de pe disk înainte de a șterge înregistrarea din BD
        if (post.getAttachmentPath() != null) {
            fileStorageService.delete(post.getAttachmentPath());
        }

        // Ștergere cascadă manuală: comentarii și reacții
        commentRepository.deleteAllByPost(post);
        reactionRepository.deleteAllByPost(post);
        postRepository.delete(post);

        log.info("BOARD_DELETE postId={} channel={}", postId, post.getChannel());
    }

    // ── getHistory (compatibilitate cu codul vechi) ───────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<PostOutDto> getHistory(String channel, int limit, Principal principal) {
        // Delegăm la getFeed și convertim — pentru backward compatibility
        return self.getFeed(channel, limit, principal).stream()
                .map(card -> new PostOutDto(
                        card.getId(), card.getChannel(),
                        card.getAuthorId(), card.getAuthorName(), card.getAuthorRole(),
                        card.getContent(), card.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND,
                        "Comentariul cu id " + commentId + " nu există."));
        commentRepository.delete(comment);
        log.info("BOARD_COMMENT_DELETE commentId={} postId={}", commentId, comment.getPost().getId());
    }

    // ── Helpers: construire DTO-uri ───────────────────────────────────────────

    /**
     * Construiește un PostCardDto complet pentru un post:
     * încarcă comentariile și calculează sumarul reacțiilor.
     */
    private PostCardDto buildFullCard(Post post, User currentUser) {
        List<Comment> comments   = commentRepository.findByPostOrderByCreatedAtAsc(post);
        ReactionSummaryDto reactions = buildReactionSummary(post, currentUser);

        List<CommentDto> commentDtos = comments.stream()
                .map(c -> toCommentDto(c, c.getAuthor()))
                .toList();

        return toCardDto(post, post.getAuthor(), commentDtos, reactions);
    }

    /**
     * Calculează sumarul reacțiilor unui post pentru un utilizator specific.
     * myReaction = tipul reacției utilizatorului curent (null dacă nu a reacționat).
     */
    private ReactionSummaryDto buildReactionSummary(Post post, User currentUser) {
        List<Reaction> allReactions = reactionRepository.findByPost(post);

        // Agregare: count per tip
        Map<String, Long> counts = allReactions.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getType().name(),
                        Collectors.counting()
                ));

        // Reacția utilizatorului curent
        String myReaction = allReactions.stream()
                .filter(r -> r.getUser().getIdUser() == currentUser.getIdUser())
                .map(r -> r.getType().name())
                .findFirst()
                .orElse(null);

        long total = allReactions.size();

        return new ReactionSummaryDto(counts, myReaction, total);
    }

    private ReactionSummaryDto buildEmptyReactions() {
        return new ReactionSummaryDto(Collections.emptyMap(), null, 0);
    }

    private PostCardDto toCardDto(Post post, User author,
                                  List<CommentDto> comments,
                                  ReactionSummaryDto reactions) {
        String attachmentUrl = post.getAttachmentPath() != null
                ? uploadUrlPrefix + "/" + post.getAttachmentPath()
                : null;

        return new PostCardDto(
                post.getId(),
                post.getChannel(),
                author.getIdUser(),
                author.getFirstName() + " " + author.getLastName(),
                roleName(author),
                post.getContent(),
                post.getCreatedAt(),
                post.getEditedAt(),
                attachmentUrl,
                post.getAttachmentType(),
                comments,
                reactions
        );
    }

    private CommentDto toCommentDto(Comment comment, User author) {
        return new CommentDto(
                comment.getId(),
                comment.getPost().getId(),
                author.getIdUser(),
                author.getFirstName() + " " + author.getLastName(),
                roleName(author),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getEditedAt()
        );
    }

    private PostOutDto toDto(Post post, User author) {
        return new PostOutDto(
                post.getId(), post.getChannel(),
                author.getIdUser(),
                author.getFirstName() + " " + author.getLastName(),
                roleName(author),
                post.getContent(),
                post.getCreatedAt()
        );
    }

    // ── Helpers: permisiuni ───────────────────────────────────────────────────

    private void checkWritePermission(String channel, User user) {
        String role = roleName(user);
        switch (channel) {
            case CHANNEL_GENERAL -> {}
            case CHANNEL_ANNOUNCEMENTS -> {
                if (!"ADMIN".equals(role) && !"TEACHER".equals(role)) {
                    throw new BusinessException(ErrorCode.ACCESS_DENIED,
                            "Doar administratorii și profesorii pot posta anunțuri.");
                }
            }
            default -> {
                if (channel.startsWith(CHANNEL_GROUP_PREFIX)) {
                    checkGroupMembership(channel, user);
                } else {
                    throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            "Canal invalid: " + channel);
                }
            }
        }
    }

    private void checkReadPermission(String channel, User user) {
        if (channel.equals(CHANNEL_GENERAL) || channel.equals(CHANNEL_ANNOUNCEMENTS)) return;
        if (channel.startsWith(CHANNEL_GROUP_PREFIX)) {
            checkGroupMembership(channel, user);
        } else {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Canal invalid: " + channel);
        }
    }

    private void checkGroupMembership(String channel, User user) {
        int groupId;
        try {
            groupId = Integer.parseInt(channel.substring(CHANNEL_GROUP_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Format canal de grupă invalid: " + channel);
        }

        String role = roleName(user);
        if ("ADMIN".equals(role)) return;

        if (!groupClassRepository.existsById(groupId)) {
            throw new BusinessException(ErrorCode.GROUP_NOT_FOUND,
                    "Grupa cu id " + groupId + " nu există.");
        }

        if ("TEACHER".equals(role)) {
            if (!groupClassRepository.existsByIdGroupAndTeacher_IdUser(groupId, user.getIdUser())) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED,
                        "Nu predați la această grupă.");
            }
            return;
        }

        if (!childGroupRepository.existsByChild_Parent_IdUserAndGroup_IdGroupAndActiveTrue(
                user.getIdUser(), groupId)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED,
                    "Nu aveți acces la canalul acestei grupe.");
        }
    }

    private User requireUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARENT_NOT_FOUND,
                        "Utilizatorul autentificat nu a fost găsit."));
    }

    private Post requirePost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND,
                        "Postarea cu id " + postId + " nu există."));
    }

    private String roleName(User user) {
        if (user.getRole() == null || user.getRole().getRoleName() == null) return "";
        return user.getRole().getRoleName().toUpperCase();
    }

    /**
     * Trimite un post nou sau actualizat pe canalul său WebSocket.
     * Topic: /topic/board/{channel}
     * Frontend înlocuiește sau adaugă cardul corespunzător.
     */
    private void broadcastPost(String channel, PostCardDto card) {
        messagingTemplate.convertAndSend(TOPIC_BOARD_PREFIX + channel, card);
    }

    /**
     * Trimite un comentariu nou pe topicul dedicat al canalului.
     * Topic: /topic/board/{channel}/comments
     * Payload: { postId, comment: CommentDto }
     * Frontend adaugă comentariul la cardul cu postId corespunzător.
     */
    private void broadcastComment(String channel, Long postId, CommentDto dto) {
        Map<String, Object> payload = Map.of("postId", postId, "comment", dto);
        messagingTemplate.convertAndSend(
                TOPIC_BOARD_PREFIX + channel + "/comments", (Object) payload);
    }

    /**
     * Trimite sumarul actualizat de reacții pe topicul dedicat al canalului.
     * Topic: /topic/board/{channel}/reactions
     * Payload: { postId, reactions: ReactionSummaryDto }
     * Frontend actualizează reacțiile cardului cu postId corespunzător.
     */
    private void broadcastReactions(String channel, Long postId, ReactionSummaryDto summary) {
        Map<String, Object> payload = Map.of("postId", postId, "reactions", summary);
        messagingTemplate.convertAndSend(
                TOPIC_BOARD_PREFIX + channel + "/reactions", (Object) payload);
    }
}
