package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.dto.WaitlistEntryResponse;
import com.springapp.proiectcrm.dto.WaitlistRequest;
import com.springapp.proiectcrm.service.WaitlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller public pentru lista de așteptare.
 *
 * Endpoint:
 *   POST /api/public/waitlist → înregistrare cerere nouă
 *
 * /api/public/** este permis fără autentificare în SecurityConfig.
 * Validarea câmpurilor (@Valid) este delegată la WaitlistRequest.
 */
@RestController
@RequestMapping("/api/public/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    /**
     * Înregistrează un părinte și copilul său pe lista de așteptare.
     * Returnat HTTP 201 Created cu datele salvate.
     */
    @PostMapping
    public ResponseEntity<WaitlistEntryResponse> addToWaitlist(
            @Valid @RequestBody WaitlistRequest request) {

        WaitlistEntryResponse response = waitlistService.addToWaitlist(request);
        return ResponseEntity.status(201).body(response);
    }
}
