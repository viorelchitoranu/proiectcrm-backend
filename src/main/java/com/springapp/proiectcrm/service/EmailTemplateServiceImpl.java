package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.exception.BusinessException;
import com.springapp.proiectcrm.exception.ErrorCode;
import com.springapp.proiectcrm.model.EmailTemplate;
import com.springapp.proiectcrm.repository.EmailTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Implementare serviciu template-uri email.
 *
 * Înlocuire variabile:
 *   Template: "Bună {{firstName}} {{lastName}}, bine ai venit în {{platformName}}!"
 *   Vars:     {"firstName": "Ion", "lastName": "Pop", "platformName": "Young Engineers"}
 *   Rezultat: "Bună Ion Pop, bine ai venit în Young Engineers!"
 *
 * Variabilele necunoscute (fără corespondent în map) sunt lăsate neschimbate
 * — adminul vede că a uitat să completeze o variabilă.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailTemplateServiceImpl implements EmailTemplateService {

    private final EmailTemplateRepository emailTemplateRepository;

    @Override
    @Transactional(readOnly = true)
    public List<EmailTemplate> findAll() {
        return emailTemplateRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public EmailTemplate findById(Integer id) {
        return emailTemplateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Template-ul cu id " + id + " nu există."
                ));
    }

    @Override
    @Transactional
    public EmailTemplate update(Integer id, String subject, String body) {
        EmailTemplate template = findById(id);

        if (subject == null || subject.isBlank()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Subiectul emailului nu poate fi gol."
            );
        }
        if (body == null || body.isBlank()) {
            throw new BusinessException(
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Corpul emailului nu poate fi gol."
            );
        }

        template.setSubject(subject.trim());
        template.setBody(body.trim());
        EmailTemplate saved = emailTemplateRepository.save(template);

        log.info("EMAIL_TEMPLATE_UPDATED code={} id={}", saved.getCode(), saved.getId());
        return saved;
    }

    @Override
    public ProcessedTemplate process(EmailTemplate template, Map<String, String> vars) {
        String subject = replaceVars(template.getSubject(), vars);
        String body    = replaceVars(template.getBody(), vars);
        return new ProcessedTemplate(subject, body);
    }

    @Override
    public ProcessedTemplate processByCode(String code, Map<String, String> vars) {
        return emailTemplateRepository.findByCode(code)
                .map(template -> process(template, vars))
                .orElse(null); // null = EmailServiceImpl folosește fallback hardcodat
    }

    /**
     * Înlocuiește toate aparițiile {{key}} din text cu valoarea corespunzătoare din vars.
     * Variabilele fără corespondent sunt lăsate neschimbate.
     *
     * @param text textul cu variabile (subject sau body)
     * @param vars map key → valoare
     * @return textul cu variabilele înlocuite
     */
    private String replaceVars(String text, Map<String, String> vars) {
        if (text == null || vars == null || vars.isEmpty()) return text;

        String result = text;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value       = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
}
