package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.dto.CalendarEventResponse;
import com.springapp.proiectcrm.service.AdminCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller pentru datele calendarului admin.
 *
 * GET /api/admin/calendar/events?start=2026-03-01&end=2026-03-31
 * → sesiuni + zile libere pentru intervalul dat
 */
@RestController
@RequestMapping("/api/admin/calendar")
@RequiredArgsConstructor
public class AdminCalendarController {

    private final AdminCalendarService adminCalendarService;

    @GetMapping("/events")
    public List<CalendarEventResponse> getEvents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end
    ) {
        return adminCalendarService.getEvents(start, end);
    }
}
