package com.springapp.proiectcrm.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Endpoint intern pentru OPS Dashboard — expune logurile CRM.
 *
 * Rută: GET /api/internal/logs
 * Securitate: ActuatorApiKeyFilter (X-OPS-API-KEY header)
 *             NU necesită autentificare Spring Security (role)
 *             NU e accesibil din frontend-ul clientului
 *
 * Diferențe față de AdminLogController (șters):
 *   - Filtru automat pe ultimele 24 ore + nivel ERROR/WARN
 *   - Protejat cu API Key, nu cu rol ADMIN
 *   - Returnat format structurat pentru OPS Dashboard
 */
@RestController
@RequestMapping("/api/internal")
@Slf4j
public class InternalLogController {

    private static final DateTimeFormatter LOG_DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${logging.file.path:./logs}")
    private String logFilePath;

    /**
     * Returnează ultimele erori și warning-uri din ultimele 24 ore.
     *
     * GET /api/internal/logs?hours=24&maxLines=200
     *
     * Răspuns:
     * {
     *   "lines": ["2026-03-29 ERROR ...", ...],
     *   "totalReturned": 45,
     *   "hoursBack": 24,
     *   "generatedAt": "2026-03-29T19:00:00"
     * }
     */
    @GetMapping("/logs")
    public ResponseEntity<Object> getLogs(
            @RequestParam(defaultValue = "24")  int hoursBack,
            @RequestParam(defaultValue = "500") int maxLines
    ) {
        int safeMaxLines = Math.min(Math.max(maxLines, 1), 2000);
        int safeHours    = Math.min(Math.max(hoursBack, 1), 168); // max 7 zile

        // Încearcă error.log primul, fallback la app.log
        Path errorLog = Path.of(logFilePath, "error.log");
        Path appLog   = Path.of(logFilePath, "app.log");

        Path logPath = Files.exists(errorLog) ? errorLog
                     : Files.exists(appLog)   ? appLog
                     : null;

        if (logPath == null) {
            return ResponseEntity.ok(Map.of(
                    "lines",         List.of(),
                    "totalReturned", 0,
                    "hoursBack",     safeHours,
                    "generatedAt",   LocalDateTime.now().toString(),
                    "note",          "Niciun fișier de log găsit la: " + logFilePath
            ));
        }

        try {
            List<String> allLines = readLastLines(logPath, safeMaxLines);

            // Filtrare: doar ERROR și WARN din ultimele X ore
            String cutoffDate = LocalDateTime.now()
                    .minusHours(safeHours)
                    .format(LOG_DATE_FMT);

            List<String> filtered = allLines.stream()
                    .filter(line -> line.contains(" ERROR ") || line.contains(" WARN "))
                    .filter(line -> line.compareTo(cutoffDate) >= 0 || !line.matches("\\d{4}-.*"))
                    .toList();

            return ResponseEntity.ok(Map.of(
                    "lines",         filtered,
                    "totalReturned", filtered.size(),
                    "hoursBack",     safeHours,
                    "generatedAt",   LocalDateTime.now().toString()
            ));

        } catch (IOException e) {
            log.error("Eroare la citirea log-urilor pentru OPS: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Eroare la citirea log-urilor: " + e.getMessage()));
        }
    }

    // ── Helper: citire ultimele N linii eficient (tail) ───────────────────────

    private List<String> readLastLines(Path path, int maxLines) throws IOException {
        List<String> lines = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            long fileLength = raf.length();
            if (fileLength == 0) return lines;

            long pointer = fileLength - 1;
            StringBuilder sb = new StringBuilder();

            while (pointer >= 0 && lines.size() < maxLines) {
                raf.seek(pointer);
                byte b = raf.readByte();

                if (b == '\n') {
                    String line = sb.reverse().toString().trim();
                    if (!line.isEmpty()) lines.add(line);
                    sb.setLength(0);
                } else if (b != '\r') {
                    sb.append((char) b);
                }
                pointer--;
            }

            if (sb.length() > 0) lines.add(sb.reverse().toString().trim());
        }

        Collections.reverse(lines);
        return lines;
    }
}
