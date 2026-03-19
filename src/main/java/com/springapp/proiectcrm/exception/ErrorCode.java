package com.springapp.proiectcrm.exception;

public enum ErrorCode {
    // Email / user
    EMAIL_ALREADY_EXISTS,
    USER_NOT_PARENT_ROLE,

    // Group / enrollment
    GROUP_NOT_FOUND,
    GROUP_INACTIVE,
    GROUP_ENDED,
    GROUP_FULL,
    CHILD_ALREADY_ENROLLED,
    CHILD_HAS_INACTIVE_ENROLLMENT,

    // Child / parent
    CHILD_NOT_FOUND,
    PARENT_NOT_FOUND,

    // Generic / validation
    BUSINESS_RULE_VIOLATION,
    VALIDATION_ERROR,
    INVALID_ENROLLMENT_REQUEST,
    TOO_MANY_CHILDREN_IN_REQUEST,
    DUPLICATE_CHILD_IN_REQUEST,

    // TEACHER
    TEACHER_NOT_FOUND,
    ACCESS_DENIED,
    CHILD_NOT_IN_GROUP,
    INVALID_OLD_PASSWORD,
    SESSION_NOT_FOUND,

    INVALID_CREDENTIALS,

    // ─── Modul 1: Capacitate grupă ───────────────────────────────────────────
    // Noua capacitate solicitată este mai mică decât numărul de locuri deja ocupate
    CAPACITY_BELOW_ENROLLED,

    // ─── Modul 3: Schimbare email ─────────────────────────────────────────────
    // Noul email este identic cu cel curent
    EMAIL_SAME_AS_CURRENT,

    // ─── Modul 4: Dezactivare cont ────────────────────────────────────────────
    // Contul este deja în starea solicitată (dezactivat sau activ)
    PARENT_ALREADY_INACTIVE,
    PARENT_ALREADY_ACTIVE,

    // ── Dezactivare COPIL individual (funcționalitate nouă) ───────────────────
    // Adminul încearcă să dezactiveze un copil care este deja dezactivat
    CHILD_ALREADY_INACTIVE,
    // Adminul încearcă să reactiveze un copil care este deja activ
    CHILD_ALREADY_ACTIVE,
    // Adminul încearcă să înscrie un copil dezactivat individual într-o grupă
    // (trebuie mai întâi reactivat)
    CHILD_IS_INACTIVE,

    // ── Cereri sesiune de la părinte ──────────────────────────────────────────
    // Părintele a trimis deja o cerere de anulare PENDING pentru această sesiune.
    // Un părinte poate trimite O SINGURĂ cerere de anulare per sesiune;
    // dacă dorește să revină, trebuie să contacteze administratorul.
    CANCEL_ALREADY_REQUESTED,

    // Părintele a trimis deja o cerere de recuperare PENDING pentru această sesiune.
    // Un părinte poate trimite O SINGURĂ cerere de recuperare per sesiune;
    // profesorul va aloca sesiunea de recuperare după ce vede cererea.
    RECOVERY_ALREADY_REQUESTED,

    // ── Listă de așteptare ────────────────────────────────────────────────────
    // Același email este deja înregistrat cu status WAITING
    // (un părinte nu poate trimite două cereri active simultan)
    WAITLIST_ALREADY_REGISTERED,
    // Cererea cu ID-ul specificat nu există în BD
    WAITLIST_ENTRY_NOT_FOUND,

    // ── Modul 5: Message Board ────────────────────────────────────────────────
    // Postarea nu a fost găsită (la ștergere de către admin)
    POST_NOT_FOUND,


}
