package com.springapp.proiectcrm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configurare Spring MVC pentru servirea fișierelor statice uploadate.
 *
 * Mapare:
 *   URL /uploads/**  →  folderul fizic {upload.dir}/
 *
 * Exemplu:
 *   Fișier salvat ca: {upload.dir}/board/abc123.pdf
 *   Accesibil la URL: http://localhost:8080/uploads/board/abc123.pdf
 *
 * ATENTIE la producție:
 *   Dacă aplicația rulează în spatele unui reverse proxy (Nginx, Apache),
 *   proxy-ul poate servi /uploads/** direct din filesystem mai eficient
 *   decât Spring. Configurat în SecurityConfig: /uploads/** este permitAll()
 *   pentru a putea fi accesate de browser fără sesiune autentificată.
 *
 *   SECURITATE: fișierele uploadate sunt accesibile fără autentificare!
 *   Motivul: imaginile trebuie să se încarce direct în browser (tag <img src=...>)
 *   fără a trimite cookies în request-ul de imagine.
 *   Dacă dorești restricție de acces → implementează download printr-un endpoint
 *   autenticat în loc de servire statică.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${upload.dir:./uploads}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Normalizăm calea: asigurăm că se termină cu /
        String location = uploadDir.endsWith("/")
                ? "file:" + uploadDir
                : "file:" + uploadDir + "/";

        registry
                .addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                // Cache 1 oră în browser — fișierele nu se modifică (UUID unic)
                .setCachePeriod(3600);
    }
}
