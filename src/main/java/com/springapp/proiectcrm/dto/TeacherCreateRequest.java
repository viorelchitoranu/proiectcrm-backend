package com.springapp.proiectcrm.dto;

import lombok.Data;

@Data
public class TeacherCreateRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String address;
    private String password;
}
