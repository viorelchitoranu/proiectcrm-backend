package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GroupDeleteSafeResponse {
    private int groupId;
    private long archivedAttendances;
    private long deletedAttendances;
    private long deletedTeacherSessions;
    private long deletedSessions;
    private long deletedChildGroupLinks;
    private String message;
}
