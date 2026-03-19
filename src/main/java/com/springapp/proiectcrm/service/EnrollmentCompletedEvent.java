package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.EnrollmentItemResponse;

import java.util.List;

public record EnrollmentCompletedEvent(
        String parentEmail,
        String parentFirstName,
        String parentLastName,
        String rawPassword,
        List<EnrollmentItemResponse> enrollments
) {
}
