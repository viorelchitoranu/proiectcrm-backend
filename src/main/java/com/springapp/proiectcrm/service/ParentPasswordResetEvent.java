package com.springapp.proiectcrm.service;

public record ParentPasswordResetEvent(
        String parentEmail,
        String parentFirstName,
        String parentLastName,
        String rawNewPassword
) {
}
