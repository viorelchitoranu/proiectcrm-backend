package com.springapp.proiectcrm.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entitate pentru template-urile de email editabile din interfața admin.
 *
 * Fiecare template corespunde unui tip de email trimis de platformă.
 * Adminul poate modifica subject și body din UI fără redeploy.
 *
 * Variabilele dinamice din subject/body (ex: {{firstName}}) sunt
 * înlocuite automat de EmailTemplateService înainte de trimitere.
 */
@Entity
@Table(name = "email_template")
@Getter
@Setter
public class EmailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * Cod unic al template-ului — folosit de EmailServiceImpl pentru căutare.
     * Ex: "PARENT_CREDENTIALS", "TEACHER_PASSWORD_RESET"
     * Nu se modifică din UI — e cheia de lookup.
     */
    @Column(nullable = false, unique = true, length = 64)
    private String code;

    /**
     * Numele afișat în interfața admin.
     * Ex: "Cont părinte creat", "Parolă profesor resetată"
     */
    @Column(nullable = false, length = 128)
    private String name;

    /**
     * Subiectul emailului — poate conține variabile {{variabila}}.
     * Ex: "Contul tău de părinte a fost creat — {{platformName}}"
     */
    @Column(nullable = false, length = 255)
    private String subject;

    /**
     * Corpul emailului — poate conține variabile {{variabila}}.
     * Stocat ca text plain (nu HTML) — trimis prin SimpleMailMessage.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /**
     * Lista variabilelor disponibile pentru acest template.
     * Afișată în UI ca hint pentru admin.
     * Ex: "{{firstName}}, {{lastName}}, {{email}}, {{password}}"
     */
    @Column(nullable = false, length = 512)
    private String availableVars;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
