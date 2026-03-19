package com.springapp.proiectcrm.logging;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

/**
 * Aspect AOP pentru logging automat al operațiilor critice și detectarea apelurilor lente.
 *
 * Există două tipuri de advice în acest aspect:
 *
 * 1. @Around pe TOATE metodele publice din servicii (com.springapp.proiectcrm.service.*)
 *    → Măsoară timpul de execuție
 *    → Dacă execuția durează > 500ms → WARN (posibilă problemă de performanță)
 *    → Nu loghează metodele rapide (INFO noise reduction)
 *
 * 2. @Before pe metodele critice specifice (înscrieri, dezactivări, mutări)
 *    → INFO la intrarea în metodă cu parametrii relevanți
 *    → Permite audit: cine a acționat și ce a schimbat
 *
 * De ce AOP și nu log.info() direct în fiecare serviciu?
 *   - Nu modifică logica de business din servicii
 *   - Un singur loc pentru politica de logging — ușor de schimbat
 *   - Se aplică automat pe metode noi fără să uiți să adaugi log
 *
 * Dependință necesară în pom.xml:
 *   <dependency>
 *       <groupId>org.springframework.boot</groupId>
 *       <artifactId>spring-boot-starter-aop</artifactId>
 *   </dependency>
 */
@Aspect
@Component
@Slf4j
public class AuditLoggingAspect {

    // ── Threshold pentru detectarea apelurilor lente ──────────────────────────
    // Orice metodă de serviciu care durează mai mult de 500ms primește WARN
    private static final long SLOW_CALL_THRESHOLD_MS = 500L;

    // ══════════════════════════════════════════════════════════════════════════
    // Detectare apeluri lente — @Around pe toate serviciile
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Măsoară timpul de execuție al oricărei metode publice din servicii.
     *
     * Pointcut: execution(public * com.springapp.proiectcrm.service.*.*(..))
     *   → orice metodă publică din orice clasă din pachetul service
     *   → exclude metodele private și protected
     *
     * Comportament:
     *   - execuție < 500ms  → nu loghează nimic (fără zgomot)
     *   - execuție >= 500ms → WARN cu durata și numele metodei
     *
     * @param pjp ProceedingJoinPoint — permite atât execuția metodei cât și accesul la metadata
     */
    @Around("execution(public * com.springapp.proiectcrm.service.*.*(..))")
    public Object measureExecutionTime(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();

        try {
            // Execuția metodei reale — ORICE excepție aruncată de metodă se propagă normal
            return pjp.proceed();
        } finally {
            // finally asigură că măsurăm timpul chiar dacă metoda aruncă excepție
            long durationMs = System.currentTimeMillis() - start;

            if (durationMs >= SLOW_CALL_THRESHOLD_MS) {
                // Extragem className scurt (fără pachet) pentru lizibilitate în log
                String className  = pjp.getTarget().getClass().getSimpleName();
                String methodName = pjp.getSignature().getName();

                log.warn("SLOW_SERVICE_CALL class={} method={} duration={}ms (threshold={}ms)",
                        className, methodName, durationMs, SLOW_CALL_THRESHOLD_MS);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Audit operații critice — @Before specific pe metode
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Loghează înscrierea unui nou set de copii (EnrollmentService.enrollChildren).
     *
     * Evenimentul este critic: creează cont de utilizator + înregistrări în BD.
     * Util pentru audit: câte înscrieri, de la ce IP (din MDC via requestId).
     *
     * args(request) — injectează parametrul metodei pentru a extrage email-ul.
     */
    @Before("execution(* com.springapp.proiectcrm.service.EnrollmentServiceImpl.enrollChildren(..)) && args(request)")
    public void logEnrollment(JoinPoint jp, Object request) {
        // Extragem emailul prin reflecție pentru a nu crea o dependință tare pe EnrollmentRequest
        // (ar necesita importul DTO-ului în aspect, cuplând logging cu business)
        String email = extractFieldSafely(request, "parentEmail");
        log.info("ENROLL_START parentEmail={}", email);
    }

    /**
     * Loghează mutarea / alocarea unui copil într-o grupă nouă.
     * childId vine din @PathVariable, transformatde controller în parametrul serviciului.
     */
    @Before("execution(* com.springapp.proiectcrm.service.AdminChildServiceImpl.moveChild(..)) && args(childId, request)")
    public void logMoveChild(JoinPoint jp, int childId, Object request) {
        String toGroupId = extractFieldSafely(request, "toGroupId");
        log.info("MOVE_CHILD_START childId={} toGroupId={}", childId, toGroupId);
    }

    /**
     * Loghează dezactivarea individuală a unui copil.
     * Operație critică de audit: eliberează locuri și schimbă starea.
     */
    @Before("execution(* com.springapp.proiectcrm.service.AdminChildServiceImpl.deactivateChild(..)) && args(childId)")
    public void logDeactivateChild(JoinPoint jp, int childId) {
        log.info("DEACTIVATE_CHILD_START childId={}", childId);
    }

    /**
     * Loghează reactivarea individuală a unui copil.
     */
    @Before("execution(* com.springapp.proiectcrm.service.AdminChildServiceImpl.activateChild(..)) && args(childId)")
    public void logActivateChild(JoinPoint jp, int childId) {
        log.info("ACTIVATE_CHILD_START childId={}", childId);
    }

    /**
     * Loghează dezactivarea contului unui părinte (afectează toți copiii).
     */
    @Before("execution(* com.springapp.proiectcrm.service.AdminParentServiceImpl.deactivateParent(..)) && args(parentId)")
    public void logDeactivateParent(JoinPoint jp, int parentId) {
        log.info("DEACTIVATE_PARENT_START parentId={}", parentId);
    }

    /**
     * Loghează reactivarea contului unui părinte.
     */
    @Before("execution(* com.springapp.proiectcrm.service.AdminParentServiceImpl.activateParent(..)) && args(parentId)")
    public void logActivateParent(JoinPoint jp, int parentId) {
        log.info("ACTIVATE_PARENT_START parentId={}", parentId);
    }

    /**
     * Loghează schimbarea email-ului unui părinte.
     * Nu loghăm noul email (GDPR) — el apare mascat în MDC via userEmail.
     */
    @Before("execution(* com.springapp.proiectcrm.service.AdminParentServiceImpl.changeParentEmail(..)) && args(parentId, request)")
    public void logChangeEmail(JoinPoint jp, int parentId, Object request) {
        log.info("CHANGE_EMAIL_START parentId={}", parentId);
    }

    // ── Helper privat ─────────────────────────────────────────────────────────

    /**
     * Extrage o valoare de câmp dintr-un obiect prin reflecție, fără să arunce excepție.
     *
     * Motivul pentru reflecție: aspect-ul nu ar trebui să depindă de clasele DTO specifice.
     * Dacă un DTO se redenumește sau se șterge, aspect-ul continuă să funcționeze
     * (returnează "?" în loc să provoace compilare eșuată).
     *
     * @param obj       obiectul din care extragem câmpul
     * @param fieldName numele câmpului
     * @return valoarea ca String, sau "?" dacă câmpul nu există sau nu e accesibil
     */
    private String extractFieldSafely(Object obj, String fieldName) {
        if (obj == null) return "?";
        try {
            var field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object val = field.get(obj);
            return val != null ? String.valueOf(val) : "null";
        } catch (Exception e) {
            // Câmpul nu există în acest DTO — nu e o eroare, returnam placeholder
            return "?";
        }
    }
}
