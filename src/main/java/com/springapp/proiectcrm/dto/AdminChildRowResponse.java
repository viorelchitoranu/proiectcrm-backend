package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDate;

/**
 * DTO pentru un rând din tabelul de copii (liste paginate și panoul de detalii părinte).
 *
 * Conține date despre:
 *   - copilul însuși (id, nume, vârstă, școală)
 *   - părintele asociat (id, nume, email, telefon)
 *   - grupa ACTIVĂ curentă, dacă există (groupId, groupName, enrollmentDate)
 *   - starea copilului (active) — CÂMP NOU față de versiunea anterioară
 *
 * Câmpul `active`:
 *   true  (sau null) → copilul este activ, poate fi înscris în grupe
 *   false            → copilul a fost dezactivat individual de admin;
 *                       toate grupele lui au fost eliberate la dezactivare
 *
 * ATENȚIE pentru developeri: constructorul folosește @AllArgsConstructor (Lombok),
 * deci ordinea câmpurilor contează. Dacă adaugi/muți câmpuri, verifică TOATE
 * locurile unde se construiesc instanțe:
 *   - AdminChildServiceImpl.getChildren()
 *   - AdminChildServiceImpl.getChildDetails()
 *   - AdminParentServiceImpl.getParentDetails()
 *   - AdminParentServiceImpl.addChildToParent()
 */
@Data
@AllArgsConstructor
public class AdminChildRowResponse {

    // ── Date copil ────────────────────────────────────────────────────────────
    private Integer   childId;
    private String    childFirstName;
    private String    childLastName;
    private Integer   age;
    private String    school;
    private String    schoolClass;

    // ── Date părinte ──────────────────────────────────────────────────────────
    private Integer   parentId;
    private String    parentName;    // "Popescu Ion" — prenume + nume concatenat
    private String    parentEmail;
    private String    parentPhone;

    // ── Grupa activă curentă (null dacă nu e înscris nicăieri) ───────────────
    private Integer   groupId;
    private String    groupName;
    private LocalDate enrollmentDate;

    // ── Starea individuală a copilului (CÂMP NOU) ─────────────────────────────
    // Câmp adăugat odată cu funcționalitatea de dezactivare per-copil.
    // null este tratat ca true (compatibilitate cu înregistrările vechi).
    private Boolean   active;
}