package com.springapp.proiectcrm.dto;

import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@AllArgsConstructor
public class GroupAdminResponse {
    private int id;
    private String name;

    private String courseName;
    private String schoolName;
    private String teacherName;

    private LocalDate startDate;
    private LocalDate endDate;
    private LocalTime sessionStartTime;

    private Integer maxCapacity;
    private Double sessionPrice;
    private Double totalPrice;

    private Boolean active;
    private LocalDateTime createdAt;

    // butonul START
    private LocalDateTime startConfirmedAt;

    // METRICE
    private long totalSessions;        // toate sesiunile generate pentru grupa
    private long heldSessions;         // sesiuni tinta
    private long cancelledSessions;    // sesiuni anulate
    private int progressPercent;       // (heldSessions / totalSessions) * 100

    private long activeChildren;       // copii inscrisi activ in grupa

    // status textual simplu pentru UI
    // "NOT_STARTED" / "ONGOING" / "FINISHED"-etc
    private String status;

    private Integer teacherId;

    private Integer schoolId;

    private Integer maxRecoverySlots;

    private LocalDateTime forceStopAt;

    private long usedRecoverySlots; // recuperari ocupate (future)


}
