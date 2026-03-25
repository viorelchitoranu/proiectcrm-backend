package com.springapp.proiectcrm.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Controller care expune conținutul fișierelor de log din server
 * pentru vizualizare în pagina React de admin (/admin/logs).
 *
 * Endpoint: GET /api/admin/logs
 * Acces: ROLE_ADMIN (configurat în SecurityConfig: /api/admin/** → hasRole("ADMIN"))
 *
 * Parametri query:
 *   file   → "app" sau "error" (default: "error")
 *   lines  → numărul de linii de la final (default: 200, max: 1000)
 *   filter → text de filtrare case-insensitive (optional)
 *            Ex: filter=ERROR, filter=ion***@***.ro, filter=DEACTIVATE
 *
 * Cum funcționează citirea eficientă din fișiere mari:
 *   Fișierele de log pot ajunge la sute de MB. Nu citim tot fișierul în memorie.
 *   Folosim RandomAccessFile pentru a citi de la finalul fișierului înapoi,
 *   similar cu comanda Unix `tail -n 200 error.log`.
 *   Citim maxim `lines` linii din coadă, nu întregul fișier.
 *
 * Calea fișierului vine din application.properties:
 *   logging.file.path=/var/log/proiectcrm  (prod)
 *   logging.file.path=./logs               (dev)
 *
 * Securitate:
 *   - Doar ROLE_ADMIN poate accesa acest endpoint
 *   - Validăm că fișierul cerut este EXACT "app" sau "error" (path traversal prevention)
 *   - Nu expunem calea completă din server în răspuns
 */
@RestController
@RequestMapping("/api/admin/logs")
@Slf4j
public class AdminLogController {

    private static final String PARAM_ERROR    = "error";
    private static final String FILE_APP       = "app";
    private static final String FILE_ERROR     = "error";

    /** Calea fișierelor de log — configurată în application.properties */
    @Value("${logging.file.path:./logs}")
    private String logFilePath;

    /**
     * Returnează ultimele N linii din fișierul de log selectat.
     *
     * Răspuns JSON:
     * {
     *   "lines": ["linie1", "linie2", ...],
     *   "totalReturned": 150,
     *   "file": "error",
     *   "filtered": true/false
     * }
     *
     * Erori:
     *   400 → fișier invalid (nu "app" sau "error")
     *   404 → fișierul nu există pe server
     *   500 → eroare la citire
     *
     * @param file   "app" sau "error"
     * @param lines  numărul de linii de citit de la final (max 1000)
     * @param filter text de filtrare (optional)
     */
    @GetMapping
    public ResponseEntity<Object> getLogs(
            @RequestParam(defaultValue = "error")  String file,
            @RequestParam(defaultValue = "200")    int    lines,
            @RequestParam(required = false)        String filter
    ) {
        // ── Validare parametri ────────────────────────────────────────────────

        // Validare strictă a numelui fișierului — previne path traversal attack
        // (ex: file=../../etc/passwd ar putea citi fișiere sensibile de pe server)
        if (!FILE_APP.equals(file) && !FILE_ERROR.equals(file)) {
            return ResponseEntity.badRequest()
                    .body(Map.of(PARAM_ERROR, "Fisier invalid. Valori acceptate: 'app', 'error'"));
        }

        // Limitare la maxim 1000 linii — protecție contra timeout și memorie
        int maxLines = Math.min(Math.max(lines, 1), 1000);

        // ── Construire cale fișier ────────────────────────────────────────────
        Path logPath = Path.of(logFilePath, file + ".log");

        if (!Files.exists(logPath)) {
            // Fișierul poate să nu existe în dev (nu s-a scris niciun log încă)
            return ResponseEntity.status(404)
                    .body(Map.of(PARAM_ERROR, "Fisierul " + file + ".log nu exista pe server.",
                            "path", logFilePath));
        }

        // ── Citire ultimele N linii ───────────────────────────────────────────
        try {
            List<String> allLines = readLastLines(logPath, maxLines);

            // Aplicare filtrare dacă s-a trimis parametrul filter
            List<String> result;
            boolean isFiltered = (filter != null && !filter.isBlank());

            if (isFiltered) {
                String filterLower = filter.toLowerCase();
                result = allLines.stream()
                        .filter(line -> line.toLowerCase().contains(filterLower))
                        .toList();
            } else {
                result = allLines;
            }

            return ResponseEntity.ok(Map.of(
                    "lines",         result,
                    "totalReturned", result.size(),
                    "file",          file,
                    "filtered",      isFiltered
            ));

        } catch (IOException e) {
            log.error("Eroare la citirea fisierului de log: {}", logPath, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(PARAM_ERROR, "Eroare la citirea fisierului de log."));
        }
    }

    // ── Helper: citire ultimele N linii eficient ──────────────────────────────

    /**
     * Citește ultimele `maxLines` linii dintr-un fișier fără să încarce tot fișierul în memorie.
     *
     * Algoritmul:
     *   1. Deschidem fișierul cu RandomAccessFile (permite seek la orice poziție)
     *   2. Pornim de la sfârșitul fișierului și mergem înapoi
     *   3. Citim byte cu byte până găsim `\n` — fiecare newline = o linie nouă
     *   4. Oprim când am colectat `maxLines` linii sau am ajuns la începutul fișierului
     *   5. Inversăm lista (am colectat invers, de la coadă spre cap)
     *
     * Eficiență: citim cel mult maxLines linii × lungimea medie a unei linii (~200 bytes)
     * Nu citim niciodată tot fișierul, indiferent de dimensiunea lui.
     *
     * @param path     calea fișierului
     * @param maxLines numărul maxim de linii de returnat
     * @return lista liniilor în ordine cronologică (cea mai veche prima)
     */
    private List<String> readLastLines(Path path, int maxLines) throws IOException {
        List<String> lines = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            long fileLength = raf.length();
            if (fileLength == 0) return lines;  // fișier gol

            long pointer = fileLength - 1;
            StringBuilder sb = new StringBuilder();

            while (pointer >= 0 && lines.size() < maxLines) {
                raf.seek(pointer);
                byte b = raf.readByte();

                if (b == '\n') {
                    // Am găsit sfârșitul unei linii (citind de la coadă)
                    // sb conține linia în ordine inversă — o inversăm și o adăugăm
                    String line = sb.reverse().toString().trim();
                    if (!line.isEmpty()) {
                        lines.add(line);
                    }
                    sb.setLength(0);  // resetăm StringBuilder pentru linia următoare
                } else if (b != '\r') {
                    // Ignorăm \r (Windows line endings) — adăugăm restul caracterelor
                    sb.append((char) b);
                }

                pointer--;
            }

            // Adăugăm ultima linie dacă fișierul nu se termina cu \n
            if (sb.length() > 0) {
                lines.add(sb.reverse().toString().trim());
            }
        }

        // Am colectat liniile de la coadă spre cap — inversăm pentru ordine cronologică
        Collections.reverse(lines);
        return lines;
    }
}
