package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.dto.*;

import com.springapp.proiectcrm.service.HolidayAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


import java.util.List;

@RestController
@RequestMapping("/api/admin/holidays")
@RequiredArgsConstructor
public class AdminHolidayController {

    private final HolidayAdminService holidayAdminService;

    @PostMapping
    public HolidayResponse createHoliday(@RequestBody HolidayCreateRequest request) {
        return holidayAdminService.createHoliday(request);
    }

    @PostMapping("/range")
    public List<HolidayResponse> createHolidayRange(@RequestBody HolidayRangeRequest request) {
        return holidayAdminService.createHolidayRange(request);
    }

    @GetMapping
    public List<HolidayResponse> getAllHolidays() {
        return holidayAdminService.getAllHolidays();
    }

    @GetMapping("/{holidayId}/sessions")
    public List<HolidayAffectedSessionResponse> getSessionsForHoliday(@PathVariable int holidayId) {
        return holidayAdminService.getAffectedSessions(holidayId);
    }

    @PostMapping("/{holidayId}/cancel-sessions")
    public int cancelSessionsForHoliday(
            @PathVariable int holidayId,
            @RequestBody HolidayCancelSessionsRequest request
    ) {

        return holidayAdminService.applyHolidayToSessions(holidayId, request);
    }



    @PostMapping("/preview")
    public HolidayPreviewResponse previewHoliday(@RequestBody HolidayCreateRequest request) {
        return holidayAdminService.previewHoliday(request);
    }

    @PostMapping("/preview-range")
    public HolidayPreviewResponse previewHolidayRange(@RequestBody HolidayRangeRequest request) {
        return holidayAdminService.previewHolidayRange(request);
    }
}
