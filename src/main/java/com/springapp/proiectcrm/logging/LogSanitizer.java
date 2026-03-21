package com.springapp.proiectcrm.logging;

/**
 * Utilitar pentru sanitizarea datelor venite de la utilizatori înainte de logare.
 *
 * Problema (Log Injection / CWE-117):
 *   Un utilizator malițios poate trimite input cu caractere newline (\n, \r)
 *   sau alte caractere de control care pot crea intrări false în fișierele de log.
 *   Ex: email = "user@test.com\nINFO [req:fake] [anon] - FAKE_LOG_ENTRY"
 *   → produce două linii în log, falsificând auditul.
 *
 * Soluție:
 *   Orice string provenit din input utilizator (email, URL, mesaje, nume fișiere,
 *   parametri HTTP) trebuie sanitizat cu sanitize() înainte de a fi inclus în log.
 *
 * Utilizare:
 *   import static com.springapp.proiectcrm.logging.LogSanitizer.sanitize;
 *   log.warn("USER_ACTION email={}", sanitize(userEmail));
 *
 * Ce sanitizăm:
 *   \n → spațiu  (newline — cel mai comun vector de atac)
 *   \r → spațiu  (carriage return)
 *   \t → spațiu  (tab — poate alinia coloane false în log)
 *
 * Ce NU sanitizăm:
 *   Date generate intern (IDs, timestamps, constante enum) — nu vin de la utilizator.
 *   Mesaje de excepție interne (ex: NullPointerException) — generate de JVM, nu de user.
 */
public final class LogSanitizer {

    private LogSanitizer() {
        // Clasă utilitară — nu se instanțiază
    }

    /**
     * Sanitizează un string pentru logare sigură.
     * Elimină caracterele de control care permit log injection.
     *
     * @param input  valoarea de sanitizat (poate fi null)
     * @return       string-ul cu \n \r \t înlocuite cu spațiu, sau "null" dacă input e null
     */
    public static String sanitize(String input) {
        if (input == null) return "null";
        return input.replaceAll("[\n\r\t]", " ");
    }
}
