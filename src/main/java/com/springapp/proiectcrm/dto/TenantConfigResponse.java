package com.springapp.proiectcrm.dto;

/**
 * DTO pentru răspunsul endpoint-ului GET /api/public/tenant-config.
 *
 * Conține DOAR date de branding — niciodată date sensibile.
 * Câmpurile cu valori goale ("") sunt gestionate de frontend cu fallback-uri.
 *
 * @param name          Numele organizației (ex: "Young Engineers București")
 * @param logoUrl       URL-ul logo-ului (gol = afișează text în loc de logo)
 * @param primaryColor  Culoarea primară hex (ex: "#e63946")
 * @param secondaryColor Culoarea secundară hex (gol = derivată automat de frontend)
 * @param website       Site-ul organizației (gol = nu se afișează link)
 * @param phone         Telefonul de contact (gol = nu se afișează)
 * @param supportEmail  Emailul de suport (gol = nu se afișează)
 */
public record TenantConfigResponse(
        String name,
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        String website,
        String phone,
        String supportEmail
) {}
