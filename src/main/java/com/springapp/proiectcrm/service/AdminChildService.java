package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.*;
import java.util.List;

/**
 * Serviciu pentru operațiunile admin pe copii.
 *
 * Metode existente:
 *   getChildren()   → lista paginată cu filtrare
 *   getChildDetails() → profilul complet cu istoricul grupelor
 *   moveChild()     → mutare / alocare copil într-o altă grupă
 *
 * Metode noi (dezactivare per-copil):
 *   deactivateChild() → dezactivează un copil individual + eliberează locurile din grupe
 *   activateChild()   → reactivează un copil; re-înscrierea în grupe se face manual
 */
public interface AdminChildService {

    // ── Existente ─────────────────────────────────────────────────────────────

    AdminPagedResponse<AdminChildRowResponse> getChildren(String query, int page, int size);

    AdminChildDetailsResponse getChildDetails(int childId);

    AdminMoveChildResponse moveChild(int childId, AdminMoveChildRequest request);

    // ── Dezactivare / reactivare per-copil (NOI) ──────────────────────────────

    /**
     * Dezactivează un copil individual.
     *
     * Efecte:
     *   1. Toate ChildGroup-urile active ale copilului → active=false (locuri eliberate)
     *   2. child.active → false
     *
     * Guard-uri:
     *   - CHILD_NOT_FOUND dacă childId nu există
     *   - CHILD_ALREADY_INACTIVE dacă child.active este deja false
     *
     * @param childId ID-ul copilului de dezactivat
     * @return ChildActivationResponse cu numărul de locuri eliberate și timestamp
     */
    ChildActivationResponse deactivateChild(int childId);

    /**
     * Reactivează un copil individual.
     *
     * Efecte:
     *   1. child.active → true
     *   (Re-înscrierea în grupe se face MANUAL de admin — locurile pot fi deja ocupate)
     *
     * Guard-uri:
     *   - CHILD_NOT_FOUND dacă childId nu există
     *   - CHILD_ALREADY_ACTIVE dacă child.active este deja true (sau null)
     *
     * @param childId ID-ul copilului de reactivat
     * @return ChildActivationResponse cu affectedEnrollments=0 și timestamp
     */
    ChildActivationResponse activateChild(int childId);
}
