package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.dto.CourseCreateRequest;
import com.springapp.proiectcrm.dto.CourseResponse;
import com.springapp.proiectcrm.model.Course;
import com.springapp.proiectcrm.repository.CourseRepository;
import com.springapp.proiectcrm.repository.GroupClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/courses")
@RequiredArgsConstructor
public class AdminCourseController {
    private final CourseRepository courseRepository;
    private final GroupClassRepository groupClassRepository;
    private static final String COURSE_NOT_FOUND = "Course not found";


    @GetMapping
    public List<CourseResponse> getAllCourses() {
        return courseRepository.findAll().stream()
                .map(c -> new CourseResponse(
                        c.getIdCourse(),
                        c.getName(),
                        c.getCourseCreatedAt()
                ))
                .toList();
    }


    @PostMapping
    public CourseResponse createCourse(@RequestBody CourseCreateRequest request) {
        Course course = new Course();
        course.setName(request.getName());
        course.setCourseCreatedAt(LocalDate.now());

        Course saved = courseRepository.save(course);
        return new CourseResponse(
                saved.getIdCourse(),
                saved.getName(),
                saved.getCourseCreatedAt()
        );

    }

    @GetMapping("/{id}")
    public CourseResponse getCourseById(@PathVariable int id) {
        Course course = courseRepository.findById(id).orElseThrow(
                () -> new IllegalArgumentException(COURSE_NOT_FOUND));
        return new CourseResponse(
                course.getIdCourse(),
                course.getName(),
                course.getCourseCreatedAt()
        );
    }

    @PutMapping("/{id}")
    public CourseResponse updateCourse(
            @PathVariable int id,
            @RequestBody CourseCreateRequest request
    ) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(COURSE_NOT_FOUND));

        course.setName(request.getName());
        Course saved = courseRepository.save(course);

        return new CourseResponse(
                saved.getIdCourse(),
                saved.getName(),
                saved.getCourseCreatedAt()
        );
    }

    @DeleteMapping("/{id}")
    public void deleteCourse(@PathVariable int id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(COURSE_NOT_FOUND));

        boolean hasGroups = !groupClassRepository.findByCourse(course).isEmpty();
        if (hasGroups) {
            throw new IllegalStateException("Nu poți șterge cursul. Există grupe asociate acestui curs.");
        }

        courseRepository.delete(course);
    }



}
