package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.EnrollmentItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class EnrollmentMailListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleEnrollmentCompleted(EnrollmentCompletedEvent event) {
        try {
            sendCredentialsEmail(event);
        } catch (Exception ex) {
            System.err.println("Eroare la trimiterea email-ului cu credențiale părinte: " + ex.getMessage());
        }

        try {
            sendSummaryEmail(event);
        } catch (Exception ex) {
            System.err.println("Eroare la trimiterea email-ului de confirmare înscriere: " + ex.getMessage());
        }
    }

    private void sendCredentialsEmail(EnrollmentCompletedEvent event) {
        String subject = "Contul tău de părinte a fost creat";
        String text = """
                Bună %s %s,

                Ți-a fost creat un cont de părinte în platformă.

                Date de autentificare:
                - Email: %s
                - Parolă: %s

                Îți recomandăm să îți schimbi parola după primul login, din secțiunea „Profil”.

                Cu drag,
                Echipa organizatoare
                """.formatted(
                event.parentFirstName(),
                event.parentLastName(),
                event.parentEmail(),
                event.rawPassword()
        );

        emailService.sendEnrollmentEmail(event.parentEmail(), subject, text);
    }

    private void sendSummaryEmail(EnrollmentCompletedEvent event) {
        String subject = "Confirmare înscriere copii";

        StringBuilder body = new StringBuilder();
        body.append("Bună, ")
                .append(event.parentFirstName()).append(" ").append(event.parentLastName()).append(",\n\n")
                .append("Înscrierea a fost efectuată cu succes pentru:\n");

        for (EnrollmentItemResponse item : event.enrollments()) {
            body.append("- ")
                    .append(item.getChildFirstName()).append(" ")
                    .append(item.getChildLastName())
                    .append(" → ")
                    .append(item.getGroupName())
                    .append("\n");
        }

        body.append("\nMulțumim,\nEchipa Top Kids / Young Engineers");

        emailService.sendEnrollmentEmail(event.parentEmail(), subject, body.toString());
    }
}
