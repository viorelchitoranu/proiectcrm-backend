package com.springapp.proiectcrm.model;

/**
 * Stările posibile ale unei cereri din lista de așteptare.
 *
 *   WAITING   → cererea a fost trimisă și așteaptă procesare de admin
 *   ALLOCATED → adminul a alocat copilul la o grupă; contul a fost creat
 *   CANCELLED → adminul a anulat cererea (ex: părintele nu mai e disponibil)
 */
public enum WaitlistStatus {
    WAITING,
    ALLOCATED,
    CANCELLED
}
