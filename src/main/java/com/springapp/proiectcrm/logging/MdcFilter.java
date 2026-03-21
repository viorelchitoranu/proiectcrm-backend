package com.springapp.proiectcrm.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtru HTTP care injectează date contextuale în MDC (Mapped Diagnostic Context)
 * la începutul fiecărui request și le curăță la final.
 *
 * MDC este un map thread-local gestionat de Logback care adaugă automat
 * aceleași date contextuale la FIECARE linie de log, fără să le pasăm explicit.
 *
 * Keys injectate (vizibile în log ca [req:...] [user:...] [METHOD /path]):
 *   requestId  → UUID scurt (8 caractere) — corelează toate logurile unui request
 *   userEmail  → email mascat GDPR: ion.popescu@gmail.com → ion***@***.ro
 *                "anon" pentru request-uri publice (enroll, login)
 *   httpMethod → GET / POST / PATCH / DELETE
 *   httpPath   → /api/admin/children/5/deactivate
 *
 * CRITIC — MDC.clear() în finally:
 *   Spring Boot refolosește thread-uri din pool pentru request-uri diferite.
 *   Fără clear(), un thread refolosit ar purta MDC-ul request-ului ANTERIOR,
 *   poluând logurile cu requestId și user greșit.
 *
 * @Order(1) — rulează primul, înainte de Spring Security, astfel MDC e populat
 * înainte de orice alt cod.
 */
@Component
@Order(1)
public class MdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // ── Setare requestId ──────────────────────────────────────────────
            // 8 caractere sunt suficiente pentru unicitate în loguri practice
            // (UUID complet de 36 car. ar fi mai sigur dar prea lung pentru lizibilitate)
            String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            MDC.put("requestId", requestId);

            // ── Setare method și path ─────────────────────────────────────────
            MDC.put("httpMethod", request.getMethod());
            MDC.put("httpPath",   request.getRequestURI());

            // ── Setare user din Spring Security ──────────────────────────────
            // La Order(1) Security poate să nu fie procesat încă, dar pentru
            // request-uri cu sesiune activă getName() returnează email-ul.
            // Request-urile publice (enroll, login) primesc "anon".
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal())) {
                MDC.put("userEmail", maskEmail(auth.getName()));
            } else {
                MDC.put("userEmail", "anon");
            }

            filterChain.doFilter(request, response);

        } finally {
            // OBLIGATORIU: fara clear() thread pool-ul refoloseste MDC din request anterior
            MDC.clear();
        }
    }

    // ── Helper: mascare email GDPR ────────────────────────────────────────────

    /**
     * Maschează parțial emailul pentru conformitate GDPR în loguri.
     *
     * Exemple:
     *   ion.popescu@gmail.com  →  ion***@***.com
     *   ab@yahoo.ro            →  ab***@***.ro
     *   a@x.com                →  a***@***.com
     *
     * Logica: primele 3 caractere din local + "***" @ "***" + TLD
     */
    public static String maskEmail(String email) {
        if (email == null) return "null";

        // SECURITATE: eliminăm newline-uri înainte de orice procesare
        // Previne log injection indiferent de unde e apelată metoda
        String sanitized = email.replaceAll("[\n\r\t]", " ");

        int atIdx = sanitized.indexOf('@');
        if (atIdx <= 0) return "***";
        String local = sanitized.substring(0, atIdx);
        String domain = sanitized.substring(atIdx);
        if (local.length() <= 3) return local.charAt(0) + "***" + domain;
        return local.substring(0, 3) + "***" + domain;
    }
}
