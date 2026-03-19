package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.dto.SchoolCreateRequest;
import com.springapp.proiectcrm.dto.SchoolResponse;
import com.springapp.proiectcrm.model.School;
import com.springapp.proiectcrm.repository.GroupClassRepository;
import com.springapp.proiectcrm.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/schools")
@RequiredArgsConstructor

public class AdminSchoolController {

    private final SchoolRepository schoolRepository;
    private final GroupClassRepository groupClassRepository;

    @PostMapping
    public SchoolResponse createSchool(@RequestBody SchoolCreateRequest request) {
        School school = new School();
        school.setName(request.getName());
        school.setAddress(request.getAddress());

        School saved = schoolRepository.save(school);

        return new SchoolResponse(
                saved.getIdSchool(),
                saved.getName(),
                saved.getAddress()
        );
    }

    @GetMapping
    public List<SchoolResponse> getAllSchools() {
        return schoolRepository.findAll().stream()
                .map(s -> new SchoolResponse(
                        s.getIdSchool(),
                        s.getName(),
                        s.getAddress()
                ))
                .toList();
    }

    @PutMapping("/{id}")
    public SchoolResponse updateSchool(
            @PathVariable int id,
            @RequestBody SchoolCreateRequest request
    ) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("School not found"));

        school.setName(request.getName());
        school.setAddress(request.getAddress());

        School saved = schoolRepository.save(school);

        return new SchoolResponse(
                saved.getIdSchool(),
                saved.getName(),
                saved.getAddress()
        );
    }

    @DeleteMapping("/{id}")
    public void deleteSchool(@PathVariable int id) {
        School school = schoolRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("School not found"));

        boolean hasGroups = !groupClassRepository.findBySchool(school).isEmpty();
        if (hasGroups) {
            throw new IllegalStateException("Nu poți șterge școala. Există grupe asociate acestei școli.");
        }

        schoolRepository.delete(school);
    }


}