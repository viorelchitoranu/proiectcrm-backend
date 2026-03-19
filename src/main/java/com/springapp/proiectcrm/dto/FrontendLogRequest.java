package com.springapp.proiectcrm.dto;

import lombok.Data;

/**
 * DTO pentru logurile trimise din frontend (React) la backend.
 *
 * Endpoint receptor: POST /api/public/client-log
 * Trimis de: logApi.js (fire-and-forget, fara retry)
 *
 * Campuri:
 *   level    → severitatea: "ERROR" | "WARN" | "INFO"
 *              Trimitem ERROR pentru JavaScript crashes (ErrorBoundary)
 *              Trimitem WARN pentru erori HTTP (4xx, 5xx de la API)
 *   message  → descrierea erorii, ex: "Cannot read properties of undefined"
 *   section  → sectiunea React unde a aparut eroarea, ex: "AdminParentsPage"
 *   url      → URL-ul paginii la momentul erorii, ex: "/admin/parents?parentId=5"
 *   stack    → stack trace JavaScript (optional, poate fi null)
 *              Util pentru debugging, dar poate fi lung — trunchiem la 1000 car.
 *
 * Securitate:
 *   Endpoint-ul este public (/api/public/**) dar nu loghează date sensibile.
 *   Nu primim parole, tokenuri sau date personale — doar erori tehnice.
 *   Rate limiting ar trebui adaugat in productie pentru a preveni spam.
 */
@Data
public class FrontendLogRequest {

    /** Nivelul erorii: ERROR, WARN, INFO */
    private String level;

    /** Mesajul erorii sau descrierea evenimentului */
    private String message;

    /** Sectiunea / pagina React unde a aparut eroarea */
    private String section;

    /** URL-ul browser-ului la momentul erorii */
    private String url;

    /** Stack trace JavaScript (optional) — trunchiat la backend la 1000 caractere */
    private String stack;
}
