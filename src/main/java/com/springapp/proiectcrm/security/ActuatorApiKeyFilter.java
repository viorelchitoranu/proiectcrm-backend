package com.springapp.proiectcrm.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtru care protejează /actuator/** și /api/internal/** cu un API Key secret.
 *
 * OPS Dashboard-ul trimite header-ul X-OPS-API-KEY la fiecare request.
 * Dacă key-ul lipsește sau nu coincide → 401 Unauthorized.
 *
 * Configurare obligatorie (variabilă de environment):
 *   OPS_API_KEY=<cheie generată cu: openssl rand -hex 32>
 *
 * Aplicația NU pornește dacă OPS_API_KEY lipsește sau este goală.
 *
 * Endpoint-uri protejate:
 *   /actuator/**      → metrici, health detaliat, info
 *   /api/internal/**  → loguri CRM pentru OPS Dashboard
 *
 * Excepții (fără key):
 *   /actuator/health  → public (Docker health check, load balancer)
 *
 * Referință: https://docs.spring.io/spring-boot/reference/features/external-config.html
 */
@Component
@Slf4j
public class ActuatorApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-OPS-API-KEY";

    /**
     * Fără fallback implicit — dacă OPS_API_KEY lipsește din environment,
     * Spring Boot aruncă excepție la startup (fail-fast).
     * Validarea suplimentară din @PostConstruct prinde cazul valorii goale.
     *
     * Ref: https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/value-annotations.html
     */
    @Value("${ops.api-key}")
    private String expectedApiKey;

    /**
     * Validare la startup — oprește aplicația dacă cheia este goală sau prea scurtă.
     * Rulează după injectarea dependențelor, înainte de acceptarea requesturilor.
     *
     * Ref: https://docs.spring.io/spring-framework/reference/core/beans/annotation-config/postconstruct-and-predestroy-annotations.html
     */
    @PostConstruct
    public void validateApiKey() {
        if (expectedApiKey == null || expectedApiKey.isBlank()) {
            throw new IllegalStateException(
                "[ActuatorApiKeyFilter] OPS_API_KEY este goală sau lipsește. " +
                "Setează variabila de environment OPS_API_KEY înainte de pornirea aplicației. " +
                "Generează o cheie sigură cu: openssl rand -hex 32"
            );
        }
        if (expectedApiKey.length() < 32) {
            throw new IllegalStateException(
                "[ActuatorApiKeyFilter] OPS_API_KEY este prea scurtă (minimum 32 caractere). " +
                "Generează o cheie sigură cu: openssl rand -hex 32"
            );
        }
        log.info("ActuatorApiKeyFilter inițializat — endpoint-uri /actuator/** și /api/internal/** protejate.");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Aplică filtrul doar pe /actuator/** și /api/internal/**
        boolean isActuator = path.startsWith("/actuator/");
        boolean isInternal  = path.startsWith("/api/internal/");

        if (!isActuator && !isInternal) {
            filterChain.doFilter(request, response);
            return;
        }

        // /actuator/health e permis fără API Key
        if (path.equals("/actuator/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        String receivedKey = request.getHeader(API_KEY_HEADER);

        if (receivedKey == null || !receivedKey.equals(expectedApiKey)) {
            String safePath = path.replaceAll("[\r\n]", "_");

            String safeIp = request.getRemoteAddr().replaceAll("[\r\n]", "_");
            log.warn("OPS_UNAUTHORIZED ip={} path={}", safeIp, safePath);
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Unauthorized\",\"message\":\"API Key invalid sau lipsă\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}
