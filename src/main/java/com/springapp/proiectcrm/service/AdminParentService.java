package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.AdminPagedResponse;
import com.springapp.proiectcrm.dto.AdminParentDetailsResponse;
import com.springapp.proiectcrm.dto.*;

import com.springapp.proiectcrm.dto.AdminParentSummaryResponse;


public interface AdminParentService {

    AdminPagedResponse<AdminParentSummaryResponse> getParents(String query, int page, int size);
    AdminParentDetailsResponse getParentDetails(int parentId);

// ── Modul 2: Adăugare copil ───────────────────────────────────────────────

    /**
     * Adaugă un copil nou la un părinte existent (fără înscriere în grupă).
     * @param parentId  ID-ul părintelui (trebuie să aibă rol PARENT)
     * @param request   datele copilului (prenume, nume, vârstă, școală, clasă)
     * @return          AdminChildRowResponse cu datele copilului nou creat
     */
    AdminChildRowResponse addChildToParent(int parentId, AdminAddChildRequest request);

    // ── Modul 3: Schimbare email ──────────────────────────────────────────────

    /**
     * Schimbă email-ul de autentificare al unui părinte.
     * Verifică unicitate, normalizează (trim + lowercase), trimite notificări
     * pe ambele adrese (veche + nouă).
     *
     * @param parentId  ID-ul părintelui
     * @param request   noul email
     * @return          AdminParentSummaryResponse actualizat
     */
    AdminParentSummaryResponse changeParentEmail(int parentId, AdminChangeEmailRequest request);

    // ── Modul 4: Dezactivare / reactivare ─────────────────────────────────────

    /**
     * Dezactivează contul unui părinte și eliberează toate locurile ocupate
     * de copiii lui (ChildGroup.active = false). @Transactional — operație atomică.
     *
     * @param parentId  ID-ul părintelui
     * @return          ParentActivationResponse cu numărul de înscrieri eliberate
     */
    ParentActivationResponse deactivateParent(int parentId);

    /**
     * Reactivează contul unui părinte (User.active = true).
     * NU recreează ChildGroup-urile — locurile trebuie reînscrise manual.
     *
     * @param parentId  ID-ul părintelui
     * @return          ParentActivationResponse
     */
    ParentActivationResponse activateParent(int parentId);


}
