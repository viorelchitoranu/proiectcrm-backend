package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.dto.TeacherActiveRequest;
import com.springapp.proiectcrm.dto.TeacherCreateRequest;
import com.springapp.proiectcrm.dto.TeacherPasswordUpdateRequest;
import com.springapp.proiectcrm.dto.TeacherResponse;
import com.springapp.proiectcrm.service.AdminTeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;



import java.util.List;

@RestController
@RequestMapping("/api/admin/teachers")
@RequiredArgsConstructor

public class AdminTeacherController {

    private final AdminTeacherService adminTeacherService;

    @PostMapping
    public TeacherResponse createTeacher(@RequestBody TeacherCreateRequest request) {
        return adminTeacherService.createTeacher(request);
    }

    @GetMapping
    public List<TeacherResponse> getAllTeachers(@RequestParam(required = false) Boolean active) {
        return adminTeacherService.getAllTeachers(active);
    }

    @PutMapping("/{teacherId}/password")
    public TeacherResponse updateTeacherPassword(
                                                  @PathVariable int teacherId,
                                                  @RequestBody TeacherPasswordUpdateRequest request
    ) {
        return adminTeacherService.updateTeacherPassword(teacherId, request);
    }

    @PutMapping("/{teacherId}/active")
    public TeacherResponse setTeacherActive(
                                             @PathVariable int teacherId,
                                             @RequestBody TeacherActiveRequest request
    ) {
        return adminTeacherService.setTeacherActive(teacherId, request);
    }
}