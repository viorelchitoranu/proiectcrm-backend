package com.springapp.proiectcrm.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filtru Bucket4j pentru rate limiting.
 *
 * Două politici distincte:
 *
 * 1. LOGIN (anti brute-force)
 *    Endpoint:  POST /api/auth/login
 *    Limită:    5 încercări / minut per IP
 *    Motivație: Previne atacurile de tip brute-force pe parole
 *
 * 2. API GENERAL
 *    Endpoint:  /api/** (exclusiv login)
 *    Limită:    200 request-uri / minut per IP
 *    Motivație: Previne abuzul și DDoS-ul de la un singur client
 *
 * Implementare în memorie (ConcurrentHashMap) — nu necesită Redis.
 * Potrivit pentru o singură instanță de aplicație.
 * Pentru deployment multi-instanță, migrează la Bucket4j + Redis.
 *
 * Header-uri returnate:
 *   X-RateLimit-Remaining  → tokenuri rămase în fereastra curentă
 *   X-RateLimit-Retry-After → secunde până la resetarea bucket-ului (la 429)
 */
@Component
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    // ── Bucket-uri separate per IP pentru login ───────────────────────────────
    private final Map<String, Bucket> loginBuckets  = new ConcurrentHashMap<>();

    // ── Bucket-uri separate per IP pentru API general ─────────────────────────
    private final Map<String, Bucket> apiBuckets    = new ConcurrentHashMap<>();

    // ── Configurare limite ────────────────────────────────────────────────────
    private static final int LOGIN_CAPACITY      = 5;    // max 5 login-uri/minut
    private static final int API_CAPACITY        = 200;  // max 200 req/minut

    // ── Endpoint-uri excluse de la rate limiting ──────────────────────────────
    private static final String[] EXCLUDED_PATHS = {
            "/api/public/",
            "/actuator/health",  // health check nu trebuie limitat
    };

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String ip   = extractClientIp(request);

        // Skip pentru path-uri excluse
        if (isExcluded(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── Politica 1: Login brute-force protection ──────────────────────────
        if (path.equals("/api/auth/login") && "POST".equalsIgnoreCase(request.getMethod())) {
            Bucket bucket = loginBuckets.computeIfAbsent(ip, k -> buildLoginBucket());
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

            if (!probe.isConsumed()) {
                long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
                log.warn("RATE_LIMIT_LOGIN ip={} retryAfter={}s", ip, retryAfterSeconds);
                sendRateLimitResponse(response, retryAfterSeconds,
                        "Prea multe încercări de login. Încearcă din nou în " + retryAfterSeconds + " secunde.");
                return;
            }

            response.addHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        // ── Politica 2: API general rate limiting ─────────────────────────────
        if (path.startsWith("/api/")) {
            Bucket bucket = apiBuckets.computeIfAbsent(ip, k -> buildApiBucket());
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

            if (!probe.isConsumed()) {
                long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
                log.warn("RATE_LIMIT_API ip={} path={} retryAfter={}s", ip, sanitizeLog(path), retryAfterSeconds);
                sendRateLimitResponse(response, retryAfterSeconds,
                        "Prea multe request-uri. Încearcă din nou în " + retryAfterSeconds + " secunde.");
                return;
            }

            response.addHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        }

        filterChain.doFilter(request, response);
    }

    // ── Constructori Bucket ───────────────────────────────────────────────────

    private Bucket buildLoginBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(LOGIN_CAPACITY)
                        .refillGreedy(LOGIN_CAPACITY, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private Bucket buildApiBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(API_CAPACITY)
                        .refillGreedy(API_CAPACITY, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isExcluded(String path) {
        for (String excluded : EXCLUDED_PATHS) {
            if (path.startsWith(excluded)) return true;
        }
        return false;
    }

    /**
     * Extrage IP-ul real al clientului.
     * Suportă proxy-uri și load balancer-e care setează X-Forwarded-For.
     */
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return sanitizeLog(forwarded.split(",")[0].trim());
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return sanitizeLog(realIp.trim());
        }
        return request.getRemoteAddr();
    }

    private String sanitizeLog(String value) {
        return value == null ? "" : value.replaceAll("[\r\n]", "_");
    }

    private void sendRateLimitResponse(HttpServletResponse response,
                                       long retryAfterSeconds,
                                       String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.addHeader("X-RateLimit-Retry-After", String.valueOf(retryAfterSeconds));
        response.addHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.getWriter().write(
                "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"" + message + "\"}"
        );
    }
}
