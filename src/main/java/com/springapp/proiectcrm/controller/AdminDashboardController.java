package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.dto.DashboardStatsResponse;
import com.springapp.proiectcrm.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller pentru datele dashboard-ului admin.
 * GET /api/admin/dashboard/stats → toate datele pentru cele 5 grafice
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/stats")
    public DashboardStatsResponse getStats() {
        return adminDashboardService.getDashboardStats();
    }
}
