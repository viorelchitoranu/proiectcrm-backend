package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.TeacherActiveRequest;
import com.springapp.proiectcrm.dto.TeacherCreateRequest;
import com.springapp.proiectcrm.dto.TeacherPasswordUpdateRequest;
import com.springapp.proiectcrm.dto.TeacherResponse;

import java.util.List;

public interface AdminTeacherService {

    TeacherResponse createTeacher(TeacherCreateRequest request); // Creare profesor.

    List<TeacherResponse> getAllTeachers(Boolean active); // Listare profesori

    TeacherResponse updateTeacherPassword(int teacherId, TeacherPasswordUpdateRequest request); // Reset parola

    TeacherResponse setTeacherActive(int teacherId, TeacherActiveRequest request); // Activ/inactiv cu regulă 409.
}
