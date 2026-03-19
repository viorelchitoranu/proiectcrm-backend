package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.dto.EmailCheckResponse;
import com.springapp.proiectcrm.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor

public class PublicAuthController {
    private final UserRepository userRepository;

    @GetMapping("/email/check")
    public EmailCheckResponse checkEmail(@RequestParam String email) {
        boolean exists = userRepository.existsByEmail(email);
        return new EmailCheckResponse(exists);
    }
}
