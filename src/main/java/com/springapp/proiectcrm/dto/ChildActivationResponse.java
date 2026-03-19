package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Răspuns la operațiile de dezactivare / reactivare a unui copil individual.
 *
 * Returnat de:
 *   PATCH /api/admin/children/{childId}/deactivate
 *   PATCH /api/admin/children/{childId}/activate
 *
 * Câmpuri:
 *   childId             → ID-ul copilului afectat
 *   childName           → "Popescu Ion" — util în mesajele din UI fără un fetch suplimentar
 *   parentId            → ID-ul părintelui — permite frontend-ului să reîmprospăteze
 *                         panoul de detalii al părintelui după operație
 *   active              → starea copilului DUPĂ operație (true=activ, false=inactiv)
 *   affectedEnrollments → numărul de ChildGroup-uri dezactivate (eliberare locuri).
 *                         0 la reactivare (re-înscrierea se face manual).
 *   processedAt         → timestamp-ul operației — util pentru audit
 *   message             → mesaj human-readable pentru toast/notification în UI
 */
@Data
@AllArgsConstructor
public class ChildActivationResponse {

    private Integer       childId;
    private String        childName;
    private Integer       parentId;
    private Boolean       active;
    private Integer       affectedEnrollments;
    private LocalDateTime processedAt;
    private String        message;
}

