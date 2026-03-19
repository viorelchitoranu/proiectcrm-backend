package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ParentMailListener {

    private final EmailService emailService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleParentPasswordReset(ParentPasswordResetEvent event) {
        try {
            User parent = new User();
            parent.setEmail(event.parentEmail());
            parent.setFirstName(event.parentFirstName());
            parent.setLastName(event.parentLastName());

            emailService.sendParentPasswordReset(parent, event.rawNewPassword());
        } catch (Exception ex) {
            System.err.println("Eroare la trimiterea email-ului de resetare parolă părinte: " + ex.getMessage());
        }
    }
}