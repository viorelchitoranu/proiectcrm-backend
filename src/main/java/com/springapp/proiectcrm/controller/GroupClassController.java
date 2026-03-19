package com.springapp.proiectcrm.controller;


import com.springapp.proiectcrm.dto.GroupOptionResponse;
import com.springapp.proiectcrm.model.GroupClass;
import com.springapp.proiectcrm.repository.ChildGroupRepository;
import com.springapp.proiectcrm.repository.CourseRepository;
import com.springapp.proiectcrm.repository.GroupClassRepository;
import com.springapp.proiectcrm.repository.SchoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor

public class GroupClassController {

    private final GroupClassRepository groupClassRepository;
    private final SchoolRepository schoolRepository;
    private final CourseRepository courseRepository;
    private final ChildGroupRepository childGroupRepository;

    // Toate grupele active
    @GetMapping("/active")
    public List<GroupOptionResponse> getActiveGroups() {
        LocalDate today = LocalDate.now();

        List<GroupClass> groups = groupClassRepository.findByIsActiveTrue();

        return groups.stream()
                .filter(g -> g.getGroupEndDate() == null || !today.isAfter(g.getGroupEndDate()))
                .map(this::toResponse)
                .toList();
    }

    // grupe active pentru o anumita scoala
    @GetMapping("/active/by-school/{schoolId}")
    public List<GroupOptionResponse> getActiveGroupsBySchool(@PathVariable int schoolId) {
        LocalDate today = LocalDate.now();

        var school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("School not found"));

        return groupClassRepository.findBySchoolAndIsActiveTrue(school)
                .stream()
                .filter(g -> g.getGroupEndDate() == null || !today.isAfter(g.getGroupEndDate()))
                .map(this::toResponse)
                .toList();
    }

    // grupe active pentru scoala + curs
    @GetMapping("/active/by-school/{schoolId}/course/{courseId}")
    public List<GroupOptionResponse> getActiveGroupsBySchoolAndCourse(
            @PathVariable int schoolId,
            @PathVariable int courseId
    ) {
        LocalDate today = LocalDate.now();

        var school = schoolRepository.findById(schoolId)
                .orElseThrow(() -> new IllegalArgumentException("School not found"));
        var course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));

        return groupClassRepository.findBySchoolAndCourseAndIsActiveTrue(school, course)
                .stream()
                .filter(g -> g.getGroupEndDate() == null || !today.isAfter(g.getGroupEndDate()))
                .map(this::toResponse)
                .toList();
    }

    // ---------- Mapper comun ----------

    private GroupOptionResponse toResponse(GroupClass group) {
        // Nume curs / scoala / adresa
        String courseName = group.getCourse() != null ? group.getCourse().getName() : "";
        String schoolName = group.getSchool() != null ? group.getSchool().getName() : "";
        String schoolAddress = group.getSchool() != null ? group.getSchool().getAddress() : "";

        // Capacitate & locuri ocupate
        long enrolled = childGroupRepository.countByGroupAndActiveTrue(group);

        int rawMaxCap = group.getGroupMaxCapacity(); // 0 = "nelimitat"
        Integer maxCapacity = rawMaxCap > 0 ? rawMaxCap : null;

        Integer remainingSpots = null;
        if (maxCapacity != null) {
            remainingSpots = Math.max(0, maxCapacity - (int) enrolled);
        }

        String timeStr = group.getSessionStartTime() != null
                ? group.getSessionStartTime().toString()
                : "";

        String remainingText;
        if (remainingSpots != null) {
            remainingText = remainingSpots + " locuri libere";
        } else {
            remainingText = "locuri disponibile";
        }

        // Label user-friendly pentru dropdown
        String label = String.format(
                "%s – %s %s (%s, %s)",
                courseName,
                group.getGroupName(),
                timeStr,
                schoolName,
                remainingText
        );

        return new GroupOptionResponse(
                group.getIdGroup(),
                label,
                courseName,
                group.getGroupName(),
                schoolName,
                schoolAddress,
                group.getGroupStartDate(),
                group.getGroupEndDate(),
                group.getSessionStartTime(),
                maxCapacity,
                enrolled,
                remainingSpots
        );
    }
}
