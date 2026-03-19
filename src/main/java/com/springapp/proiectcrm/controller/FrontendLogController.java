package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.dto.FrontendLogRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller care primește loguri de eroare din frontend (React) și le scrie
 * în sistemul de logging al backend-ului (Logback → fișierele de pe server).
 *
 * Endpoint: POST /api/public/client-log
 *
 * De ce este /api/public/?
 *   SecurityConfig are .requestMatchers("/api/public/**").permitAll()
 *   Erorile JavaScript pot apărea înainte de autentificare (ex: crash pe pagina de login)
 *   sau după expirarea sesiunii. Nu vrem să pierdem aceste erori din cauza unui 401.
 *
 * Cazuri de utilizare:
 *   1. JavaScript crash (ErrorBoundary.jsx) → level=ERROR, mesaj + stack trace
 *   2. Request HTTP eșuat (4xx/5xx) → level=WARN, endpoint + cod HTTP
 *
 * Toate logurile primite sunt marcate cu prefixul [FE] pentru a fi ușor
 * de identificat în fișierele de log față de logurile backend.
 *
 * Securitate:
 *   - Nu returnăm date sensibile în răspuns (200 OK simplu)
 *   - Trunchiem stack trace la 1000 caractere pentru a preveni log flooding
 *   - În producție, rate limiting ar trebui adăugat (ex: 10 req/minut/IP)
 */
@RestController
@RequestMapping("/api/public")
@Slf4j
public class FrontendLogController {

    /**
     * Primește un log de eroare din frontend și îl scrie în Logback.
     *
     * Nivelul de log ales în funcție de câmpul "level" din request:
     *   ERROR → log.error  (apare în ambele fișiere: app.log și error.log)
     *   WARN  → log.warn   (apare în ambele fișiere)
     *   altceva → log.info (apare doar în app.log)
     *
     * Formatul mesajului în fișier:
     *   [FE] section=AdminParentsPage url=/admin/parents msg=Cannot read properties... stack=...
     *
     * @param req obiectul primit din frontend via JSON body
     * @return 200 OK întotdeauna — nu vrem să blocăm UI-ul pentru erori de logging
     */
    @PostMapping("/client-log")
    public ResponseEntity<Void> receiveLog(@RequestBody FrontendLogRequest req) {

        // Sanitizare: trunchiem stack trace lung pentru a nu polua logurile
        // Un stack trace JavaScript poate fi de sute de linii
        String stack = req.getStack();
        if (stack != null && stack.length() > 1000) {
            stack = stack.substring(0, 1000) + "... [trunchiat]";
        }

        // Formăm mesajul structurat — prefix [FE] pentru identificare rapidă în grep
        String logMessage = "[FE] section={} url={} msg={} stack={}";

        // Alegem nivelul în funcție de ce trimite frontend-ul
        String level = (req.getLevel() != null) ? req.getLevel().toUpperCase() : "INFO";

        switch (level) {
            case "ERROR" -> log.error(logMessage, req.getSection(), req.getUrl(), req.getMessage(), stack);
            case "WARN"  -> log.warn (logMessage, req.getSection(), req.getUrl(), req.getMessage(), stack);
            default      -> log.info (logMessage, req.getSection(), req.getUrl(), req.getMessage(), stack);
        }

        // Returnăm 200 OK fără body — frontend nu are nevoie de confirmare
        return ResponseEntity.ok().build();
    }
}
