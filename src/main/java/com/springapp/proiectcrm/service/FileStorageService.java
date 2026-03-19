package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.exception.BusinessException;
import com.springapp.proiectcrm.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Serviciu pentru salvarea și ștergerea fișierelor atașate la posturile din Message Board.
 *
 * Fișierele permise: JPEG, PNG, PDF (conform cerințelor — doar admin poate atașa).
 * Dimensiunea maximă: 10MB (configurabilă în application.properties cu spring.servlet.multipart.max-file-size).
 *
 * Structura folderului de stocare:
 *   {upload.dir}/board/           → folder dedicat posturilor de pe board
 *   Fișierele sunt redenumite cu UUID pentru a evita coliziunile de nume.
 *   ex: "abc123-def456.pdf" în loc de "contract.pdf" (care ar putea fi suprascriere)
 *
 * URL de acces din browser:
 *   Fișierele sunt expuse prin endpoint-ul static /uploads/** configurat în WebMvcConfig.
 *   ex: fișierul salvat ca "board/abc123.pdf" → accesibil la "/uploads/board/abc123.pdf"
 *
 * ATENTIE la deployment:
 *   upload.dir trebuie să fie în afara folderului aplicației (nu în target/)
 *   pentru a supraviețui redeploy-urilor.
 *   Exemplu producție: upload.dir=/var/data/proiectcrm/uploads
 */
@Service
@Slf4j
public class FileStorageService {

    // Tipuri MIME acceptate — JPEG, PNG și PDF
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "application/pdf"
    );

    // Extensii acceptate — verificare dublă (MIME + extensie)
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".pdf"
    );

    // Dimensiunea maximă per fișier: 10MB
    private static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    // Folderul rădăcină unde se salvează fișierele
    // Configurat în application.properties: upload.dir=./uploads
    @Value("${upload.dir:./uploads}")
    private String uploadDir;

    /**
     * Salvează un fișier atașat la un post.
     *
     * Validează tipul și dimensiunea, generează un nume unic UUID,
     * salvează în {uploadDir}/board/ și returnează calea relativă.
     *
     * @param file  fișierul primit din request multipart
     * @return      calea relativă stocată în Post.attachmentPath
     *              ex: "board/abc123-def456.pdf"
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Fișierul atașat este gol.");
        }

        // Verificare dimensiune
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Fișierul depășește dimensiunea maximă permisă de 10MB.");
        }

        // Verificare tip MIME
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Tipul fișierului nu este permis. Sunt acceptate: JPEG, PNG, PDF.");
        }

        // Verificare extensie — a doua linie de apărare
        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename().toLowerCase() : "";
        String extension = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.'))
                : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Extensia fișierului nu este permisă.");
        }

        // Generare nume unic UUID — previne coliziuni și path traversal
        String uniqueFileName = UUID.randomUUID().toString().replace("-", "") + extension;
        String relativePath   = "board/" + uniqueFileName;

        try {
            // Creare folder dacă nu există
            Path boardDir = Paths.get(uploadDir, "board");
            Files.createDirectories(boardDir);

            // Salvare fișier
            Path destination = boardDir.resolve(uniqueFileName);
            Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

            log.info("FILE_STORED path={} size={} type={}", relativePath, file.getSize(), contentType);

            return relativePath;

        } catch (IOException e) {
            log.error("FILE_STORE_FAILED originalName={} error=\"{}\"",
                    file.getOriginalFilename(), e.getMessage());
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Nu am putut salva fișierul. Încearcă din nou.");
        }
    }

    /**
     * Șterge un fișier după calea sa relativă.
     * Apelat când un admin șterge un post cu atașament.
     * Eșecul ștergerii este logat ca WARN, nu aruncă excepție —
     * nu vrem să blocăm ștergerea postului dacă fișierul nu mai există.
     *
     * @param relativePath  calea relativă stocată în Post.attachmentPath
     *                      ex: "board/abc123.pdf"
     */
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return;
        try {
            Path file = Paths.get(uploadDir, relativePath);
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                log.info("FILE_DELETED path={}", relativePath);
            } else {
                log.warn("FILE_DELETE_NOT_FOUND path={}", relativePath);
            }
        } catch (IOException e) {
            // WARN în loc de ERROR — nu blocăm ștergerea postului
            log.warn("FILE_DELETE_FAILED path={} error=\"{}\"", relativePath, e.getMessage());
        }
    }

    /**
     * Determină tipul atașamentului ("IMAGE" sau "PDF") din MIME type.
     * Folosit la salvarea Post.attachmentType.
     */
    public String resolveAttachmentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null) return "PDF";
        return contentType.startsWith("image/") ? "IMAGE" : "PDF";
    }
}
