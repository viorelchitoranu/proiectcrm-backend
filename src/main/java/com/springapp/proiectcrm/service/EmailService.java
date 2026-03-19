package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.model.User;

public interface EmailService {
    void sendTeacherCredentials(User teacher, String rawPassword);

    void sendTeacherPasswordReset(User teacher, String rawNewPassword);

    void sendParentCredentials(User parent, String rawPassword);

    void sendParentPasswordReset(User parent, String rawNewPassword);

    void sendEnrollmentEmail(String to, String subject, String body);


    // ── Modul 3: Schimbare email ──────────────────────────────────────────────

    /**
     * Trimite notificare pe AMBELE adrese (veche + nouă) când un admin
     * schimbă email-ul unui cont de părinte.
     *
     * @param oldEmail  adresa veche (primește avertizare de securitate)
     * @param newEmail  adresa nouă (primește confirmare cu noul email de login)
     * @param parentName numele complet al părintelui (pentru personalizare)
     */
    void sendEmailChangedNotification(String oldEmail, String newEmail, String parentName);

    // ── Modul 4: Dezactivare / reactivare cont ────────────────────────────────

    /**
     * Trimite email de notificare când contul unui părinte este dezactivat de admin.
     * Include câte grupe au fost eliberate.
     *
     * @param parent               entitatea User cu datele părintelui
     * @param releasedEnrollments  numărul de înregistrări ChildGroup dezactivate
     */
    void sendParentDeactivated(User parent, int releasedEnrollments);

    /**
     * Trimite email de notificare când contul unui părinte este reactivat de admin.
     * Atenționează că trebuie să se reînscrie copiii dacă doresc.
     *
     * @param parent entitatea User cu datele părintelui
     */
    void sendParentReactivated(User parent);

    // ── Listă de așteptare ────────────────────────────────────────────────────

    /**
     * Trimite email de notificare când adminul alocă un copil din waitlist la o grupă.
     *
     * Dacă isNewAccount = true → emailul conține și credențialele (email + parolă temporară).
     * Dacă isNewAccount = false → emailul confirmă alocarea pe contul existent.
     *
     * @param parent         entitatea User (creat sau existent)
     * @param rawPassword    parola în clar (null dacă contul exista deja)
     * @param groupName      numele grupei la care a fost alocat copilul
     * @param isNewAccount   true dacă contul a fost creat acum, false dacă era existent
     */
    void sendWaitlistAllocated(User parent, String rawPassword,
                               String groupName, boolean isNewAccount);
}


