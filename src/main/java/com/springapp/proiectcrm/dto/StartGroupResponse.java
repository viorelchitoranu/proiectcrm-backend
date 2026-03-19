package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class StartGroupResponse {
    private int groupId;
    private String groupName;
    private LocalDateTime startConfirmedAt;
    private String message;
}
