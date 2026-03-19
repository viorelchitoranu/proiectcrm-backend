package com.springapp.proiectcrm.model;

public enum AttendanceStatus {
    PRESENT,
    ABSENT,
    EXCUSED,
    CANCELLED_BY_PARENT,
    RECOVERY_REQUESTED,   // <-- ADD
    RECOVERY_BOOKED,
    PENDING
}
