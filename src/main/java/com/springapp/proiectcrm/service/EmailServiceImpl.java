package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from-address}")
    private String fromAdress;

    // ── metode existente ───────────────────────────────────────────────────────

    @Override
    public void sendEnrollmentEmail(String to, String subject, String body) {
        sendMail(to, subject, body);
    }

    @Override
    public void sendTeacherCredentials(User teacher, String rawPassword) {
        String subject = "Cont profesor creat în platforma CRM";
        String text = """
                Bună %s %s,

                Ți-a fost creat un cont de profesor în platforma noastră.

                Date de autentificare:
                - Email: %s
                - Parolă: %s

                Te rugăm ca după prima autentificare să îți schimbi parola din secțiunea „Profil".

                Toate cele bune,
                Echipa Admin
                """.formatted(
                teacher.getFirstName(),
                teacher.getLastName(),
                teacher.getEmail(),
                rawPassword
        );
        sendMail(teacher.getEmail(), subject, text);
    }

    @Override
    public void sendTeacherPasswordReset(User teacher, String rawNewPassword) {
        String subject = "Parola ta a fost resetată (Profesor)";
        String text = """
                Bună %s %s,

                Parola contului tău de profesor a fost resetată de un administrator.

                Noua parolă este:
                - %s

                Te rugăm ca după autentificare să o modifici din secțiunea „Profil".

                Toate cele bune,
                Echipa Admin
                """.formatted(
                teacher.getFirstName(),
                teacher.getLastName(),
                rawNewPassword
        );
        sendMail(teacher.getEmail(), subject, text);
    }

    @Override
    public void sendParentCredentials(User parent, String rawPassword) {
        String subject = "Contul tău de părinte a fost creat";
        String text = """
                Bună %s %s,

                Ți-a fost creat un cont de părinte în platformă.

                Date de autentificare:
                - Email: %s
                - Parolă: %s

                Îți recomandăm să îți schimbi parola după primul login, din secțiunea „Profil".

                Cu drag,
                Echipa organizatoare
                """.formatted(
                parent.getFirstName(),
                parent.getLastName(),
                parent.getEmail(),
                rawPassword
        );
        sendMail(parent.getEmail(), subject, text);
    }

    @Override
    public void sendParentPasswordReset(User parent, String rawNewPassword) {
        String subject = "Parola contului tău de părinte a fost schimbată";
        String text = """
                Bună %s %s,

                Parola contului tău de părinte a fost actualizată.

                Noua parolă este:
                - %s

                Dacă nu ai inițiat tu această schimbare, te rugăm să ne contactezi cât mai curând.

                Cu drag,
                Echipa organizatoare
                """.formatted(
                parent.getFirstName(),
                parent.getLastName(),
                rawNewPassword
        );
        sendMail(parent.getEmail(), subject, text);
    }

    // ── Modul 3: Schimbare email ───────────────────────────────────────────────

    @Override
    public void sendEmailChangedNotification(String oldEmail, String newEmail, String parentName) {

        // Email pe adresa VECHE — avertizare de securitate
        String subjectOld = "Adresa ta de email a fost modificată";
        String textOld = """
                Bună %s,

                Un administrator a modificat adresa de email asociată contului tău.

                Noua adresă de email este: %s

                De acum înainte, folosește această adresă pentru a te autentifica în platformă.

                Dacă nu ai autorizat această modificare sau crezi că este o eroare,
                te rugăm să contactezi echipa administrativă cât mai curând.

                Cu drag,
                Echipa organizatoare
                """.formatted(parentName, newEmail);
        sendMail(oldEmail, subjectOld, textOld);

        // Email pe adresa NOUĂ — confirmare
        String subjectNew = "Confirmare: email de login actualizat";
        String textNew = """
                Bună %s,

                Această adresă de email (%s) a fost înregistrată ca adresă de autentificare
                pentru contul tău în platforma CRM.

                Folosește această adresă și parola existentă la următorul login.

                Cu drag,
                Echipa organizatoare
                """.formatted(parentName, newEmail);
        sendMail(newEmail, subjectNew, textNew);
    }

    // ── Modul 4: Dezactivare / reactivare ─────────────────────────────────────

    @Override
    public void sendParentDeactivated(User parent, int releasedEnrollments) {
        String subject = "Contul tău a fost dezactivat";
        String text = """
                Bună %s %s,

                Contul tău de părinte în platforma CRM a fost dezactivat de un administrator.

                Ca urmare a dezactivării, copiii tăi au fost dezinscrisi din %d grupă(e) activă(e).
                Istoricul prezențelor rămâne păstrat.

                Dacă crezi că această acțiune este o eroare sau dorești să reactivezi contul,
                te rugăm să contactezi echipa administrativă.

                Cu drag,
                Echipa organizatoare
                """.formatted(
                parent.getFirstName(),
                parent.getLastName(),
                releasedEnrollments
        );
        sendMail(parent.getEmail(), subject, text);
    }

    @Override
    public void sendParentReactivated(User parent) {
        String subject = "Contul tău a fost reactivat";
        String text = """
                Bună %s %s,

                Contul tău de părinte în platforma CRM a fost reactivat.

                Poți folosi din nou datele de autentificare pentru a te loga.
                Notă: înscrierea copiilor în grupe trebuie refăcută, deoarece
                locurile ocupate anterior au fost eliberate la dezactivare.

                Cu drag,
                Echipa organizatoare
                """.formatted(
                parent.getFirstName(),
                parent.getLastName()
        );
        sendMail(parent.getEmail(), subject, text);
    }

    @Override
    public void sendWaitlistAllocated(User parent, String rawPassword,
                                      String groupName, boolean isNewAccount) {
        if (isNewAccount) {
            // Cont nou creat → trimitem credențialele + confirmarea alocării
            String subject = "Ai fost înscris pe platformă și alocat la o grupă!";
            String text = """
                Bună %s %s,

                Cererea ta de pe lista de așteptare a fost procesată.
                Un administrator te-a alocat la grupa: %s

                Ți-a fost creat un cont de părinte cu următoarele date de autentificare:
                - Email: %s
                - Parolă temporară: %s

                Te rugăm să te autentifici și să îți schimbi parola din secțiunea „Profil".

                Cu drag,
                Echipa organizatoare
                """.formatted(
                    parent.getFirstName(),
                    parent.getLastName(),
                    groupName,
                    parent.getEmail(),
                    rawPassword
            );
            sendMail(parent.getEmail(), subject, text);
        } else {
            // Cont existent → doar confirmăm alocarea, fără parolă
            String subject = "Copilul tău a fost alocat la o grupă";
            String text = """
                Bună %s %s,

                Cererea ta de pe lista de așteptare a fost procesată.
                Un administrator a alocat copilul tău la grupa: %s

                Te poți autentifica cu datele existente pentru a vedea detaliile.

                Cu drag,
                Echipa organizatoare
                """.formatted(
                    parent.getFirstName(),
                    parent.getLastName(),
                    groupName
            );
            sendMail(parent.getEmail(), subject, text);
        }
    }

    // ── helper intern ──────────────────────────────────────────────────────────

    private void sendMail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        if (fromAdress != null && !fromAdress.isBlank()) {
            message.setFrom(fromAdress);
        }
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }
}
