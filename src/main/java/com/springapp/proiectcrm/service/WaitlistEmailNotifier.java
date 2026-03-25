package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Bean separat pentru trimiterea notificărilor email asincrone legate de waitlist.
 *
 * DE CE E NECESAR UN BEAN SEPARAT?
 *   @Async funcționează prin proxy Spring — când o metodă @Async este apelată
 *   din aceeași clasă (via "this"), proxy-ul Spring este ocolit și metoda
 *   rulează sincron, ignorând complet adnotarea @Async.
 *
 *   Soluția corectă este să extragi metodele @Async într-un bean separat și
 *   să îl injectezi în serviciul care are nevoie de ele.
 *
 *   Această clasă este injectată în WaitlistServiceImpl via constructor.
 *
 * COMPORTAMENT LA EROARE:
 *   Erorile de email sunt logate ca WARN și înghițite — nu se propagă.
 *   Motivul: un mail server down nu trebuie să rollback-uiască tranzacția BD.
 *   Operația principală (alocarea din waitlist) a reușit deja.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WaitlistEmailNotifier {

    private final EmailService emailService;

    /**
     * Trimite notificare asincronă la alocarea unui copil din lista de așteptare.
     * Apelat din WaitlistServiceImpl.allocateFromWaitlist() DUPĂ commit tranzacție.
     *
     * @param parent       părintele alocat
     * @param rawPassword  parola în clar (necesară dacă e cont nou) sau null
     * @param groupName    numele grupei în care a fost alocat copilul
     * @param isNewAccount true dacă s-a creat un cont nou pentru acest părinte
     */
    @Async
    public void sendAllocationEmailAsync(
            User parent, String rawPassword, String groupName, boolean isNewAccount) {
        try {
            emailService.sendWaitlistAllocated(parent, rawPassword, groupName, isNewAccount);
        } catch (Exception e) {
            log.warn("EMAIL_NOTIFICATION_FAILED event=waitlistAllocated parentId={} error=\"{}\"",
                    parent.getIdUser(), e.getMessage());
        }
    }
}
