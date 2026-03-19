package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Răspuns returnat după ce adminul alocă cu succes o intrare din waitlist.
 * Conține informații despre contul creat/refolosit și grupa la care s-a înscris copilul.
 */
@Data
@AllArgsConstructor
public class WaitlistAllocateResponse {

    private int waitlistEntryId;
    private int parentId;
    private String parentEmail;
    private int childId;
    private String childName;
    private int groupId;
    private String groupName;

    // true = contul de părinte a fost creat acum (parolă generată + email trimis)
    // false = emailul exista deja → s-a refolosit contul existent
    private boolean newAccountCreated;

    private String message;
}
