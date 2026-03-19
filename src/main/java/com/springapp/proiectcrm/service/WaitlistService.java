package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.*;

import java.util.List;

/**
 * Serviciu pentru gestionarea listei de așteptare.
 *
 * Operații publice (fără autentificare):
 *   addToWaitlist() → părintele se înscrie pe lista de așteptare
 *
 * Operații admin (necesită rol ADMIN):
 *   getAllEntries()  → lista completă pentru pagina admin
 *   allocate()      → alocă copilul la o grupă și creează contul dacă nu există
 *   cancel()        → anulează o cerere
 */
public interface WaitlistService {

    // ── Public ────────────────────────────────────────────────────────────────
    WaitlistEntryResponse addToWaitlist(WaitlistRequest request);

    // ── Admin ─────────────────────────────────────────────────────────────────
    List<WaitlistEntryResponse> getAllEntries();
    WaitlistAllocateResponse    allocate(int entryId, WaitlistAllocateRequest request);
    void                        cancel(int entryId);
}
