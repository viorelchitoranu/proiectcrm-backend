package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.dto.GroupStatsResponse;
import com.springapp.proiectcrm.service.AdminReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reports")
@RequiredArgsConstructor

public class AdminReportController {

    private final AdminReportService adminReportService;



    @GetMapping("/groups")
    public List<GroupStatsResponse> getGroupsStats() {
        return adminReportService.getGroupStats();
    }

    @GetMapping("/groups/{groupId}")
    public GroupStatsResponse getGroupStats(@PathVariable int groupId) {
        return adminReportService.getGroupStats(groupId);
    }


    @GetMapping("/groups/csv")
    public ResponseEntity<byte[]> exportGroupsStatsCsv() {
        String csv = adminReportService.exportGroupStatsAsCsv();
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=group_stats.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }
}