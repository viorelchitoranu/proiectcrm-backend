package com.springapp.proiectcrm.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configurarea tenant-ului (clientului) — toate valorile vin din environment variables.
 *
 * ──────────────────────────────────────────────────────────────────────────
 * DE CE ESTE NECESAR:
 *   Platforma este white-label — același cod rulează pentru clienți diferiți
 *   (Young Engineers, Dans Aurora, Robotică XYZ etc.).
 *   Fiecare client are propriile date de branding (nume, culori, logo, email).
 *   Aceste date sunt configurate prin environment variables în docker-compose.yml
 *   și NU sunt hardcodate în cod.
 *
 * VARIABILE DE ENVIRONMENT (configurate în .env.local / .env.production):
 *   TENANT_NAME            → numele organizației (ex: "Young Engineers București")
 *   TENANT_LOGO_URL        → URL-ul logo-ului (ex: "https://app.youngengineers.ro/logo.png")
 *   TENANT_PRIMARY_COLOR   → culoarea primară hex (ex: "#e63946")
 *   TENANT_EMAIL_FROM_NAME → numele din câmpul "From:" al emailurilor
 *   TENANT_WEBSITE         → site-ul organizației (ex: "https://youngengineers.ro")
 *   TENANT_PHONE           → telefonul de contact
 *   TENANT_SUPPORT_EMAIL   → emailul de suport (dacă diferă de emailul de trimitere)
 *
 * UTILIZARE:
 *   - EmailServiceImpl  → înlocuiește "Echipa organizatoare" cu TENANT_EMAIL_FROM_NAME
 *   - TenantController  → expune config la frontend prin /api/public/tenant-config
 * ──────────────────────────────────────────────────────────────────────────
 */
@Component
@Getter
public class TenantConfig {

    /**
     * Numele complet al organizației.
     * Apare în: titlul aplicației, emailuri, pagini de eroare.
     * Ex: "Young Engineers București"
     */
    @Value("${tenant.name:CRM Platform}")
    private String name;

    /**
     * URL-ul logo-ului organizației — servit din CDN sau din uploads.
     * Dacă e gol, frontend-ul afișează un placeholder text.
     * Ex: "https://app.youngengineers.ro/logo.png"
     */
    @Value("${tenant.logo-url:}")
    private String logoUrl;

    /**
     * Culoarea primară a temei vizuale — format hex CSS.
     * Folosită de frontend pentru butoane, accente, sidebar.
     * Ex: "#e63946" (roșu Young Engineers), "#1677ff" (albastru default Ant Design)
     */
    @Value("${tenant.primary-color:#1677ff}")
    private String primaryColor;

    /**
     * Culoarea secundară a temei — opțională.
     * Dacă nu e setată, frontend-ul derivă automat o culoare complementară.
     */
    @Value("${tenant.secondary-color:}")
    private String secondaryColor;

    /**
     * Numele care apare în câmpul "From:" al emailurilor trimise de platformă.
     * Ex: "Young Engineers" → emailul apare de la "Young Engineers <contact@...>"
     * Dacă nu e setat, se folosește tenant.name.
     */
    @Value("${tenant.email-from-name:${tenant.name:CRM Platform}}")
    private String emailFromName;

    /**
     * Site-ul web al organizației — afișat în footer și emailuri.
     * Ex: "https://youngengineers.ro"
     */
    @Value("${tenant.website:}")
    private String website;

    /**
     * Telefonul de contact al organizației — afișat în footer și emailuri.
     * Ex: "+40 700 000 000"
     */
    @Value("${tenant.phone:}")
    private String phone;

    /**
     * Emailul de suport — afișat în emailuri pentru contact.
     * Dacă nu e setat, se folosește app.mail.from-address.
     */
    @Value("${tenant.support-email:${app.mail.from-address:}}")
    private String supportEmail;
}
