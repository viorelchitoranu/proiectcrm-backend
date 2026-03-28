package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.config.TenantConfig;
import com.springapp.proiectcrm.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Implementare serviciu email — cu template-uri editabile din interfața admin.
 *
 * FLUX DE TRIMITERE:
 *   1. Construim map-ul de variabile (firstName, lastName, password etc.)
 *   2. Apelăm emailTemplateService.processByCode(code, vars)
 *   3. Dacă template-ul există în DB → folosim subject + body din DB
 *   4. Dacă template-ul NU există în DB → fallback la text hardcodat
 *      (protecție: aplicația funcționează chiar dacă BD e goală)
 *
 * VARIABILE COMUNE disponibile în toate template-urile:
 *   {{platformName}} → tenantConfig.getName()
 *   {{teamName}}     → tenantConfig.getEmailFromName()
 *
 * ADĂUGARE TEMPLATE NOU:
 *   1. Adaugă un rând în V4__email_templates.sql cu codul nou
 *   2. Adaugă constanta codului în această clasă
 *   3. Apelează processByCode() cu codul nou în metoda corespunzătoare
 */
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender         mailSender;
    private final TenantConfig           tenantConfig;// ← injectat prin constructor (@RequiredArgsConstructor)
    private final EmailTemplateService   emailTemplateService;// ← injectat prin constructor

    @Value("${app.mail.from-address}")
    private String fromAddress;

    // ── Coduri template-uri — corespund coloanei `code` din tabela email_template ──
    private static final String TPL_PARENT_CREDENTIALS                = "PARENT_CREDENTIALS";
    private static final String TPL_PARENT_PASSWORD_RESET             = "PARENT_PASSWORD_RESET";
    private static final String TPL_PARENT_RESET_OWN_PASSWORD         = "PARENT_RESET_OWN_PASSWORD";
    private static final String TPL_TEACHER_CREDENTIALS               = "TEACHER_CREDENTIALS";
    private static final String TPL_TEACHER_PASSWORD_RESET            = "TEACHER_PASSWORD_RESET";
    private static final String TPL_EMAIL_CHANGED                     = "EMAIL_CHANGED";
    private static final String TPL_WAITLIST_ALLOCATED_NEW            = "WAITLIST_ALLOCATED_NEW_ACCOUNT";
    private static final String TPL_WAITLIST_ALLOCATED_EXISTING       = "WAITLIST_ALLOCATED_EXISTING_ACCOUNT";

    // ── Variabile comune — prezente în toate template-urile ──────────────────
    private Map<String, String> commonVars() {
        return Map.of(
                "platformName", tenantConfig.getName(),
                "teamName",     tenantConfig.getEmailFromName()
        );
    }

    /** Construiește map de variabile combinând variabilele comune cu cele specifice */
    private Map<String, String> vars(Map<String, String> specific) {
        var result = new java.util.HashMap<>(commonVars());
        result.putAll(specific);
        return result;
    }

    // ── metode existente ───────────────────────────────────────────────────────

    @Override
    public void sendEnrollmentEmail(String to, String subject, String body) {
        sendMail(to, subject, body);
    }

    @Override
    public void sendTeacherCredentials(User teacher, String rawPassword) {
        var processed = emailTemplateService.processByCode(
                TPL_TEACHER_CREDENTIALS,
                vars(Map.of(
                        "firstName", teacher.getFirstName(),
                        "lastName",  teacher.getLastName(),
                        "email",     teacher.getEmail(),
                        "password",  rawPassword
                ))
        );

        if (processed != null) {
            sendMail(teacher.getEmail(), processed.subject(), processed.body());
            return;
        }

        // Fallback hardcodat
        sendMail(teacher.getEmail(),
                "Cont profesor creat în " + tenantConfig.getName(),
                """
                Bună %s %s,

                Ți-a fost creat un cont de profesor în platforma %s.

                Date de autentificare:
                - Email: %s
                - Parolă: %s

                Te rugăm ca după prima autentificare să îți schimbi parola din secțiunea „Profil".

                Toate cele bune,
                %s
                """.formatted(teacher.getFirstName(), teacher.getLastName(),
                        tenantConfig.getName(), teacher.getEmail(),
                        rawPassword, tenantConfig.getEmailFromName()));
    }

    @Override
    public void sendTeacherPasswordReset(User teacher, String rawNewPassword) {
        var processed = emailTemplateService.processByCode(
                TPL_TEACHER_PASSWORD_RESET,
                vars(Map.of(
                        "firstName", teacher.getFirstName(),
                        "lastName",  teacher.getLastName(),
                        "password",  rawNewPassword
                ))
        );

        if (processed != null) {
            sendMail(teacher.getEmail(), processed.subject(), processed.body());
            return;
        }

        sendMail(teacher.getEmail(),
                "Parola ta a fost resetată — " + tenantConfig.getName(),
                """
                Bună %s %s,

                Parola contului tău de profesor a fost resetată de un administrator.

                Noua parolă este:
                - %s

                Te rugăm ca după autentificare să o modifici din secțiunea „Profil".

                Toate cele bune,
                %s
                """.formatted(teacher.getFirstName(), teacher.getLastName(),
                        rawNewPassword, tenantConfig.getEmailFromName()));
    }

    @Override
    public void sendParentCredentials(User parent, String rawPassword) {
        var processed = emailTemplateService.processByCode(
                TPL_PARENT_CREDENTIALS,
                vars(Map.of(
                        "firstName", parent.getFirstName(),
                        "lastName",  parent.getLastName(),
                        "email",     parent.getEmail(),
                        "password",  rawPassword
                ))
        );

        if (processed != null) {
            sendMail(parent.getEmail(), processed.subject(), processed.body());
            return;
        }

        sendMail(parent.getEmail(),
                "Contul tău de părinte a fost creat — " + tenantConfig.getName(),
                """
                Bună %s %s,

                Ți-a fost creat un cont de părinte în platforma %s.

                Date de autentificare:
                - Email: %s
                - Parolă: %s

                Cu drag,
                %s
                """.formatted(parent.getFirstName(), parent.getLastName(),
                        tenantConfig.getName(), parent.getEmail(),
                        rawPassword, tenantConfig.getEmailFromName()));
    }

    @Override
    public void sendParentPasswordReset(User parent, String rawNewPassword) {
        var processed = emailTemplateService.processByCode(
                TPL_PARENT_PASSWORD_RESET,
                vars(Map.of(
                        "firstName", parent.getFirstName(),
                        "lastName",  parent.getLastName(),
                        "password",  rawNewPassword
                ))
        );

        if (processed != null) {
            sendMail(parent.getEmail(), processed.subject(), processed.body());
            return;
        }

        sendMail(parent.getEmail(),
                "Parola contului tău a fost schimbată — " + tenantConfig.getName(),
                """
                Bună %s %s,

                Parola contului tău de părinte a fost actualizată.

                Noua parolă este:
                - %s

                Cu drag,
                %s
                """.formatted(parent.getFirstName(), parent.getLastName(),
                        rawNewPassword, tenantConfig.getEmailFromName()));
    }

    @Override
    public void sendEmailChangedNotification(String oldEmail, String newEmail, String parentName) {
        var processed = emailTemplateService.processByCode(
                TPL_EMAIL_CHANGED,
                vars(Map.of(
                        "firstName", parentName,
                        "newEmail",  newEmail
                ))
        );

        if (processed != null) {
            sendMail(oldEmail, processed.subject(), processed.body());
            // Email de confirmare pe adresa nouă
            sendMail(newEmail,
                    "Confirmare: email de login actualizat — " + tenantConfig.getName(),
                    "Această adresă (" + newEmail + ") a fost înregistrată ca adresă de autentificare "
                            + "pentru contul tău în platforma " + tenantConfig.getName() + ".\n\n"
                            + "Cu drag,\n" + tenantConfig.getEmailFromName());
            return;
        }

        // Fallback
        sendMail(oldEmail,
                "Adresa ta de email a fost modificată — " + tenantConfig.getName(),
                """
                Bună %s,

                Un administrator a modificat adresa de email asociată contului tău.
                Noua adresă este: %s

                Cu drag,
                %s
                """.formatted(parentName, newEmail, tenantConfig.getEmailFromName()));

        sendMail(newEmail,
                "Confirmare: email de login actualizat — " + tenantConfig.getName(),
                """
                Bună %s,

                Această adresă (%s) a fost înregistrată ca adresă de autentificare.

                Cu drag,
                %s
                """.formatted(parentName, newEmail, tenantConfig.getEmailFromName()));
    }

    @Override
    public void sendParentDeactivated(User parent, int releasedEnrollments) {
        sendMail(parent.getEmail(),
                "Contul tău a fost dezactivat — " + tenantConfig.getName(),
                """
                Bună %s %s,

                Contul tău a fost dezactivat. Copiii tăi au fost dezinscrisi din %d grupă(e).

                Cu drag,
                %s
                """.formatted(parent.getFirstName(), parent.getLastName(),
                        releasedEnrollments, tenantConfig.getEmailFromName()));
    }

    @Override
    public void sendParentReactivated(User parent) {
        sendMail(parent.getEmail(),
                "Contul tău a fost reactivat — " + tenantConfig.getName(),
                """
                Bună %s %s,

                Contul tău a fost reactivat. Poți folosi datele de autentificare existente.

                Cu drag,
                %s
                """.formatted(parent.getFirstName(), parent.getLastName(),
                        tenantConfig.getEmailFromName()));
    }

    @Override
    public void sendWaitlistAllocated(User parent, String rawPassword,
                                      String groupName, boolean isNewAccount) {
        String code = isNewAccount ? TPL_WAITLIST_ALLOCATED_NEW : TPL_WAITLIST_ALLOCATED_EXISTING;

        var specificVars = new java.util.HashMap<String, String>();
        specificVars.put("firstName", parent.getFirstName());
        specificVars.put("lastName",  parent.getLastName());
        specificVars.put("email",     parent.getEmail());
        specificVars.put("groupName", groupName);
        if (rawPassword != null) specificVars.put("password", rawPassword);

        var processed = emailTemplateService.processByCode(code, vars(specificVars));

        if (processed != null) {
            sendMail(parent.getEmail(), processed.subject(), processed.body());
            return;
        }

        // Fallback
        if (isNewAccount) {
            sendMail(parent.getEmail(),
                    "Ai fost înscris în " + tenantConfig.getName() + " și alocat la o grupă!",
                    """
                    Bună %s %s,

                    Ai fost alocat la grupa: %s

                    Date autentificare:
                    - Email: %s
                    - Parolă: %s

                    Cu drag,
                    %s
                    """.formatted(parent.getFirstName(), parent.getLastName(),
                            groupName, parent.getEmail(), rawPassword,
                            tenantConfig.getEmailFromName()));
        } else {
            sendMail(parent.getEmail(),
                    "Copilul tău a fost alocat la o grupă — " + tenantConfig.getName(),
                    """
                    Bună %s %s,

                    Copilul tău a fost alocat la grupa: %s

                    Cu drag,
                    %s
                    """.formatted(parent.getFirstName(), parent.getLastName(),
                            groupName, tenantConfig.getEmailFromName()));
        }
    }

    // ── helper intern ──────────────────────────────────────────────────────────

    private void sendMail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        if (fromAddress != null && !fromAddress.isBlank()) {
            message.setFrom(fromAddress);
        }
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
