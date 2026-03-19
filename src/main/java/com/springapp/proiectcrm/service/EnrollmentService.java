package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.EnrollmentRequest;
import com.springapp.proiectcrm.dto.EnrollmentResponse;

public interface EnrollmentService {

    EnrollmentResponse enrollChildren(EnrollmentRequest request);
}
