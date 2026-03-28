package com.springapp.proiectcrm.repository;

import com.springapp.proiectcrm.model.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository pentru template-urile de email.
 * Căutarea principală se face după codul unic al template-ului.
 */
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Integer> {

    /**
     * Găsește un template după codul său unic.
     * Folosit de EmailServiceImpl pentru a încărca template-ul înainte de trimitere.
     *
     * @param code codul template-ului (ex: "PARENT_CREDENTIALS")
     * @return template-ul găsit sau empty dacă nu există
     */
    Optional<EmailTemplate> findByCode(String code);
}
