package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.dto.EnrollmentRequest;
import com.springapp.proiectcrm.dto.EnrollmentResponse;
import com.springapp.proiectcrm.service.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor

public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping
    public ResponseEntity<EnrollmentResponse> enrollChildren(@Valid @RequestBody EnrollmentRequest request) {
        EnrollmentResponse response = enrollmentService.enrollChildren(request);
        return ResponseEntity.ok(response);
    }

}
