package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.model.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Bean separat pentru trimiterea notificărilor email asincrone legate de părinți.
 *
 * DE CE E NECESAR UN BEAN SEPARAT?
 *   @Async funcționează prin proxy Spring — când o metodă @Async este apelată
 *   din aceeași clasă (via "this"), proxy-ul Spring este ocolit și metoda
 *   rulează sincron, ignorând complet adnotarea @Async.
 *
 *   Soluția corectă este să extragi metodele @Async într-un bean separat și
 *   să îl injectezi în serviciul care are nevoie de ele.
 *
 *   Această clasă este injectată în AdminParentServiceImpl via constructor.
 *
 * COMPORTAMENT LA EROARE:
 *   Erorile de email sunt logate ca WARN și înghițite — nu se propagă.
 *   Motivul: un mail server down nu trebuie să rollback-uiască tranzacția BD.
 *   Operația principală (schimbare email, dezactivare cont) a reușit deja.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ParentEmailNotifier {

    private final EmailService emailService;

    /**
     * Trimite notificare asincronă la schimbarea emailului.
     * Apelat din AdminParentServiceImpl.changeParentEmail() DUPĂ commit tranzacție.
     *
     * @param oldEmail   emailul vechi (pentru notificarea contului vechi)
     * @param newEmail   emailul nou (pentru confirmare)
     * @param parentName numele complet al părintelui
     */
    @Async
    public void sendEmailChangedAsync(String oldEmail, String newEmail, String parentName) {
        try {
            emailService.sendEmailChangedNotification(oldEmail, newEmail, parentName);
        } catch (Exception e) {
            log.warn("EMAIL_NOTIFICATION_FAILED event=emailChanged parentName=\"{}\" error=\"{}\"",
                    parentName, e.getMessage());
        }
    }

    /**
     * Trimite notificare asincronă la dezactivarea contului.
     * Apelat din AdminParentServiceImpl.deactivateParent() DUPĂ commit tranzacție.
     *
     * @param parent    părintele dezactivat
     * @param released  numărul de locuri de grupă eliberate
     */
    @Async
    public void sendDeactivatedAsync(User parent, int released) {
        try {
            emailService.sendParentDeactivated(parent, released);
        } catch (Exception e) {
            log.warn("EMAIL_NOTIFICATION_FAILED event=parentDeactivated parentId={} error=\"{}\"",
                    parent.getIdUser(), e.getMessage());
        }
    }

    /**
     * Trimite notificare asincronă la reactivarea contului.
     * Apelat din AdminParentServiceImpl.activateParent() DUPĂ commit tranzacție.
     *
     * @param parent  părintele reactivat
     */
    @Async
    public void sendReactivatedAsync(User parent) {
        try {
            emailService.sendParentReactivated(parent);
        } catch (Exception e) {
            log.warn("EMAIL_NOTIFICATION_FAILED event=parentReactivated parentId={} error=\"{}\"",
                    parent.getIdUser(), e.getMessage());
        }
    }
}
