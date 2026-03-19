package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ParentSessionActionResponse {
    private Integer childId;
    private Integer sessionId;
    private String action;   // "CANCEL" sau "RECOVERY_REQUEST"
    private String message;  // mesaj user-friendly
}