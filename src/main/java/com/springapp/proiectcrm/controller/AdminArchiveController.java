package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.model.AttendanceArchive;
import com.springapp.proiectcrm.repository.AttendanceArchiveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminArchiveController {

    private final AttendanceArchiveRepository attendanceArchiveRepository;

    @GetMapping("/attendance-archive")
    public List<AttendanceArchive> getAttendanceArchive(
            @RequestParam(required = false) Integer limit
    ) {
        List<AttendanceArchive> all = attendanceArchiveRepository.findAll(
                Sort.by(Sort.Direction.DESC, "archivedAt")
        );

        if (limit != null && limit > 0 && all.size() > limit) {
            return all.subList(0, limit);
        }
        return all;
    }
}
