package com.springapp.proiectcrm.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiErrorResponse {
    private LocalDateTime timestamp;
    private int status;
    private String error;    // ex: "Bad Request"
    private String message;  // mesajul detaliat pentru utilizator
    private String path;     // URL-ul care a declansat eroarea
    private String code;     // cod intern ("GROUP_ENDED", "EMAIL_EXISTS", ...)


    public static ApiErrorResponse of(int status,
                                      String error,
                                      String message,
                                      String path,
                                      String code) {
        return new ApiErrorResponse(
                LocalDateTime.now(),
                status,
                error,
                message,
                path,
                code
        );
    }
}
