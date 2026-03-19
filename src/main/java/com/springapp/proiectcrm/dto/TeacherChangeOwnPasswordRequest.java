package com.springapp.proiectcrm.dto;

import lombok.Data;

@Data
public class TeacherChangeOwnPasswordRequest {

    private String oldPassword;
    private String newPassword;
}
