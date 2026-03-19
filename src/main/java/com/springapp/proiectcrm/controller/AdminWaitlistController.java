package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.dto.WaitlistAllocateRequest;
import com.springapp.proiectcrm.dto.WaitlistAllocateResponse;
import com.springapp.proiectcrm.dto.WaitlistEntryResponse;
import com.springapp.proiectcrm.service.WaitlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller admin pentru gestionarea listei de așteptare.
 *
 * Endpoints:
 *   GET  /api/admin/waitlist              → toate cererile (newest first)
 *   POST /api/admin/waitlist/{id}/allocate → alocă copilul la o grupă
 *   POST /api/admin/waitlist/{id}/cancel   → anulează o cerere
 *
 * /api/admin/** necesită rol ADMIN (configurat în SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/waitlist")
@RequiredArgsConstructor
public class AdminWaitlistController {

    private final WaitlistService waitlistService;

    /**
     * Returnează toate cererile din lista de așteptare, sortate descrescător după dată.
     * Adminul vede atât cererile WAITING cât și pe cele ALLOCATED/CANCELLED.
     */
    @GetMapping
    public ResponseEntity<List<WaitlistEntryResponse>> getAll() {
        return ResponseEntity.ok(waitlistService.getAllEntries());
    }

    /**
     * Alocă un copil din waitlist la o grupă specificată de admin.
     * Creează contul părintelui dacă emailul nu există, sau refolosește contul existent.
     * Trimite email de notificare cu credențialele (dacă cont nou) sau confirmarea alocării.
     */
    @PostMapping("/{id}/allocate")
    public ResponseEntity<WaitlistAllocateResponse> allocate(
            @PathVariable int id,
            @Valid @RequestBody WaitlistAllocateRequest request) {

        return ResponseEntity.ok(waitlistService.allocate(id, request));
    }

    /**
     * Anulează o cerere din waitlist.
     * Nu șterge înregistrarea — o marchează cu status CANCELLED pentru audit.
     * O cerere ALLOCATED nu poate fi anulată.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable int id) {
        waitlistService.cancel(id);
        return ResponseEntity.noContent().build();
    }
}

