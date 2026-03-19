package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.dto.*;

import com.springapp.proiectcrm.service.AdminParentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/parents")
@RequiredArgsConstructor
public class AdminParentController {

    private final AdminParentService adminParentService;

    @GetMapping
    public AdminPagedResponse<AdminParentSummaryResponse> getParents(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return adminParentService.getParents(query, page, size);
    }

    @GetMapping("/{parentId}")
    public AdminParentDetailsResponse getParentDetails(@PathVariable int parentId) {
        return adminParentService.getParentDetails(parentId);
    }

    // ── Modul 2: Adăugare copil ───────────────────────────────────────────────

    /**
     * POST /api/admin/parents/{parentId}/children
     * Adaugă un copil nou la un părinte existent.
     * NU înscrie copilul în nicio grupă — înscrierea se face separat din AdminChildren.
     */
    @PostMapping("/{parentId}/children")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminChildRowResponse addChildToParent(
            @PathVariable int parentId,
            @Valid @RequestBody AdminAddChildRequest request
    ) {
        return adminParentService.addChildToParent(parentId, request);
    }

    // ── Modul 3: Schimbare email ──────────────────────────────────────────────

    /**
     * PATCH /api/admin/parents/{parentId}/email
     * Schimbă email-ul de autentificare al unui părinte.
     * Trimite notificare automată pe ambele adrese (veche + nouă).
     *
     * Avertizare: noul email devine imediat noile credențiale de login.
     * Sesiunea activă a părintelui rămâne validă până la logout.
     */
    @PatchMapping("/{parentId}/email")
    public AdminParentSummaryResponse changeParentEmail(
            @PathVariable int parentId,
            @Valid @RequestBody AdminChangeEmailRequest request
    ) {
        return adminParentService.changeParentEmail(parentId, request);
    }

    // ── Modul 4: Dezactivare / reactivare cont ────────────────────────────────

    /**
     * PATCH /api/admin/parents/{parentId}/deactivate
     * Dezactivează contul părintelui și eliberează toate locurile copiilor.
     * Operație @Transactional — atomică: ori totul, ori rollback complet.
     */
    @PatchMapping("/{parentId}/deactivate")
    public ParentActivationResponse deactivateParent(@PathVariable int parentId) {
        return adminParentService.deactivateParent(parentId);
    }

    /**
     * PATCH /api/admin/parents/{parentId}/activate
     * Reactivează contul unui părinte dezactivat.
     * NU recreează înscrierea copiilor în grupe.
     */
    @PatchMapping("/{parentId}/activate")
    public ParentActivationResponse activateParent(@PathVariable int parentId) {
        return adminParentService.activateParent(parentId);
    }



}