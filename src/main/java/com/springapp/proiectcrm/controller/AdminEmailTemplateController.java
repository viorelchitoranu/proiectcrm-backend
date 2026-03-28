package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.model.EmailTemplate;
import com.springapp.proiectcrm.service.EmailTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller REST pentru gestionarea template-urilor de email din interfața admin.
 *
 * Toate endpoint-urile sunt protejate cu @PreAuthorize("hasRole('ADMIN')").
 *
 * Endpoints:
 *   GET  /api/admin/email-templates         → lista tuturor template-urilor
 *   GET  /api/admin/email-templates/{id}    → un template după ID
 *   PUT  /api/admin/email-templates/{id}    → actualizare subject + body
 */
@RestController
@RequestMapping("/api/admin/email-templates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminEmailTemplateController {

    private final EmailTemplateService emailTemplateService;

    /**
     * Returnează toate template-urile de email.
     * Folosit de pagina admin pentru a afișa lista de template-uri editabile.
     */
    @GetMapping
    public List<EmailTemplate> getAll() {
        return emailTemplateService.findAll();
    }

    /**
     * Returnează un template după ID.
     * Folosit la deschiderea formularului de editare.
     */
    @GetMapping("/{id}")
    public EmailTemplate getById(@PathVariable Integer id) {
        return emailTemplateService.findById(id);
    }

    /**
     * Actualizează subject-ul și body-ul unui template.
     * Code-ul și availableVars nu se pot modifica din UI.
     *
     * @param id      ID-ul template-ului
     * @param request obiect cu subject și body noi
     */
    @PutMapping("/{id}")
    public EmailTemplate update(
            @PathVariable Integer id,
            @RequestBody UpdateTemplateRequest request) {
        return emailTemplateService.update(id, request.subject(), request.body());
    }

    /** DTO pentru request-ul de actualizare */
    public record UpdateTemplateRequest(String subject, String body) {}
}
