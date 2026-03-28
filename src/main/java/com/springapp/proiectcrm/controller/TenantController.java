package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.config.TenantConfig;
import com.springapp.proiectcrm.dto.TenantConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller public pentru configurarea tenant-ului.
 *
 * Endpoint-ul este PUBLIC (fără autentificare) — frontend-ul îl apelează
 * la pornire pentru a configura tema vizuală și branding-ul, ÎNAINTE
 * ca utilizatorul să se autentifice.
 *
 * Securitate: expune DOAR date de branding, niciodată date sensibile
 * (parole, chei API, etc.).
 *
 * Endpoint: GET /api/public/tenant-config
 * Răspuns:  TenantConfigResponse (JSON)
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class TenantController {

    private final TenantConfig tenantConfig;

    /**
     * Returnează configurarea de branding a tenant-ului curent.
     *
     * Apelat de frontend la pornirea aplicației (în App.jsx sau main.jsx)
     * pentru a seta:
     *   - Titlul tab-ului browser
     *   - CSS variables pentru culori (--primary-color etc.)
     *   - Logo-ul din sidebar
     *   - Numele organizației în header/footer
     *
     * @return configurarea tenant-ului ca JSON
     */
    @GetMapping("/tenant-config")
    public TenantConfigResponse getTenantConfig() {
        return new TenantConfigResponse(
                tenantConfig.getName(),
                tenantConfig.getLogoUrl(),
                tenantConfig.getPrimaryColor(),
                tenantConfig.getSecondaryColor(),
                tenantConfig.getWebsite(),
                tenantConfig.getPhone(),
                tenantConfig.getSupportEmail()
        );
    }
}
