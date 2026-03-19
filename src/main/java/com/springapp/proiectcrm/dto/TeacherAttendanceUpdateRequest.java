package com.springapp.proiectcrm.dto;

import com.springapp.proiectcrm.model.AttendanceStatus;
import lombok.Data;

import java.util.List;

@Data
public class TeacherAttendanceUpdateRequest {

    private List<Row> rows;

    @Data
    public static class Row {
        private Integer childId;
        private AttendanceStatus status; // PRESENT / ABSENT / EXCUSED
    }
}
