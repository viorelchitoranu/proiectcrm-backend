package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.dto.SchoolOptionResponse;
import com.springapp.proiectcrm.model.School;
import com.springapp.proiectcrm.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/schools")
@RequiredArgsConstructor

public class SchoolController {
    private final SchoolRepository schoolRepository;

    @GetMapping
    public List<SchoolOptionResponse> getSchools() {
        List<School> schools = schoolRepository.findAll();
        return schools.stream()
                .map(s -> new SchoolOptionResponse(
                        s.getIdSchool(),
                        s.getName(),
                        s.getAddress()
                )).toList();
    }
}
