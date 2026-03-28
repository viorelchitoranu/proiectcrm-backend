package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.model.EmailTemplate;

import java.util.List;
import java.util.Map;

/**
 * Serviciu pentru gestionarea template-urilor de email.
 *
 * Responsabilități:
 *   1. CRUD template-uri (pentru controller-ul admin)
 *   2. Înlocuire variabile {{variabila}} în subject + body
 *   3. Fallback la text hardcodat dacă template-ul nu există în DB
 */
public interface EmailTemplateService {

    /** Returnează toate template-urile — pentru pagina admin */
    List<EmailTemplate> findAll();

    /** Returnează un template după ID — pentru editare */
    EmailTemplate findById(Integer id);

    /** Actualizează subject și body unui template */
    EmailTemplate update(Integer id, String subject, String body);

    /**
     * Procesează un template: înlocuiește variabilele {{key}} cu valorile din map.
     *
     * @param template template-ul de procesat
     * @param vars     map de variabile (ex: {"firstName": "Ion", "groupName": "Robotică"})
     * @return template-ul cu variabilele înlocuite (subject + body)
     */
    ProcessedTemplate process(EmailTemplate template, Map<String, String> vars);

    /**
     * Găsește template-ul după cod și îl procesează.
     * Returnează null dacă template-ul nu există în DB
     * (EmailServiceImpl va folosi fallback hardcodat).
     */
    ProcessedTemplate processByCode(String code, Map<String, String> vars);

    /** DTO intern pentru template-ul procesat */
    record ProcessedTemplate(String subject, String body) {}
}
