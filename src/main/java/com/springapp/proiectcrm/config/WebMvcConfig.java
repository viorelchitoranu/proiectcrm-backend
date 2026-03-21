package com.springapp.proiectcrm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configurare Spring MVC.
 *
 * ATENTIE: Handler-ul static pentru /uploads/** a fost eliminat intenționat.
 * Fișierele uploadate sunt acum servite prin endpoint-ul autentificat:
 *   GET /api/files/{subfolder}/{filename}
 *
 * Motivul: /uploads/** era accesibil public (fără autentificare),
 * ceea ce reprezenta o vulnerabilitate de securitate —
 * oricine cu URL-ul putea accesa fișierele fără să fie logat.
 *
 * FileController.java gestionează servirea cu:
 *   - Verificare autentificare (prin Spring Security)
 *   - Protecție path traversal
 *   - Content-Type corect per tip de fișier
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    // Nicio configurare specială necesară momentan.
    // FileController.java gestionează servirea fișierelor autentificat.
}
