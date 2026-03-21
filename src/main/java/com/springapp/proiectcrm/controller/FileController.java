package com.springapp.proiectcrm.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Controller pentru servirea fișierelor uploadate (atașamente posturi forum).
 *
 * SECURITATE:
 *   Endpoint-ul /api/files/** este protejat de Spring Security —
 *   doar utilizatorii autentificați pot accesa fișierele.
 *   Înlocuiește servirea statică /uploads/** care era accesibilă public.
 *
 * Path traversal protection:
 *   normalize() + verificarea că path-ul rezultat începe cu uploadDir
 *   previne atacurile de tip ../../etc/passwd
 *
 * Utilizare în frontend:
 *   În loc de:  src="/uploads/board/abc123.jpg"
 *   Folosim:    src="/api/files/board/abc123.jpg"
 *
 * URL-ul e construit în MessageBoardServiceImpl.toCardDto():
 *   attachmentUrl = uploadUrlPrefix + "/" + relativePath
 *   unde uploadUrlPrefix = "/api/files" (schimbat din "/uploads")
 */
@RestController
@Slf4j
public class FileController {

    @Value("${upload.dir:./uploads}")
    private String uploadDir;

    /**
     * Servește un fișier din folderul de uploads.
     *
     * @param subfolder  ex: "board"
     * @param filename   ex: "abc123def456.pdf"
     */
    @GetMapping("/api/files/{subfolder}/{filename}")
    public ResponseEntity<Resource> serveFile(
            @PathVariable String subfolder,
            @PathVariable String filename) {

        try {
            // Construim calea absolută și normalizăm pentru a preveni path traversal
            Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path filePath   = uploadRoot.resolve(subfolder).resolve(filename).normalize();

            // SECURITATE: verificăm că path-ul final e în interiorul folderului de uploads
            // Previne atacuri de tip: /api/files/../../etc/passwd
            if (!filePath.startsWith(uploadRoot)) {
                log.warn("FILE_PATH_TRAVERSAL_ATTEMPT subfolder={} filename={}", subfolder, filename);
                return ResponseEntity.badRequest().build();
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                log.warn("FILE_NOT_FOUND path={}/{}", subfolder, filename);
                return ResponseEntity.notFound().build();
            }

            // Determinăm Content-Type din extensia fișierului
            String contentType = determineContentType(filename);

            log.debug("FILE_SERVED path={}/{} type={}", subfolder, filename, contentType);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    // inline pentru imagini (afișare în browser), attachment pentru PDF (download)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            contentType.startsWith("image/")
                                    ? "inline; filename=\"" + filename + "\""
                                    : "attachment; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (MalformedURLException e) {
            log.error("FILE_SERVE_ERROR path={}/{} error=\"{}\"", subfolder, filename, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Determină Content-Type din extensia fișierului.
     * Acceptăm doar tipurile permise la upload: JPEG, PNG, PDF.
     */
    private String determineContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".pdf"))  return "application/pdf";
        // Default sigur — nu expunem tipuri nepermise
        return "application/octet-stream";
    }
}

