package com.springapp.proiectcrm.dto;

import com.springapp.proiectcrm.model.AttendanceStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TeacherAttendanceRowResponse {
    private Integer attendanceId;
    private Integer childId;
    private String childFirstName;
    private String childLastName;

    private String parentName;
    private String parentPhone;
    private String parentEmail;

    private AttendanceStatus status;
    private boolean recovery;        // true daca e sesiune de recuperare

}
