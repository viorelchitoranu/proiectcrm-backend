package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Modul 4 — Răspuns la dezactivarea / reactivarea unui cont de părinte.
 * Returnează câte înregistrări ChildGroup au fost dezactivate / reactivate
 * pentru transparență operațională.
 */
@Data
@AllArgsConstructor
public class ParentActivationResponse {

    private Integer parentId;
    private String  parentName;

    /** true = contul este activ după operație; false = contul este inactiv */
    private Boolean active;

    /** Numărul de înregistrări ChildGroup afectate (dezactivate sau reactivate) */
    private Integer affectedEnrollments;

    /** Timestamp-ul operației */
    private LocalDateTime processedAt;

    private String message;
}

