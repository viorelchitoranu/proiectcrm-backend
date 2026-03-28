package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.CalendarEventResponse;
import com.springapp.proiectcrm.model.Holiday;
import com.springapp.proiectcrm.model.Session;
import com.springapp.proiectcrm.repository.HolidayRepository;
import com.springapp.proiectcrm.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Serviciu pentru evenimentele calendarului admin.
 * Combină sesiunile și zilele libere dintr-un interval dat.
 */
@Service
@RequiredArgsConstructor
public class AdminCalendarService {

    private final SessionRepository sessionRepository;
    private final HolidayRepository holidayRepository;

    @Transactional(readOnly = true)
    public List<CalendarEventResponse> getEvents(LocalDate start, LocalDate end) {
        List<CalendarEventResponse> events = new ArrayList<>();

        // ── Sesiuni din interval ──────────────────────────────────────────────
        List<Session> sessions = sessionRepository
                .findBySessionDateBetween(start, end);

        for (Session s : sessions) {
            String groupName   = s.getGroup() != null ? s.getGroup().getGroupName() : "—";
            String teacherName = (s.getGroup() != null && s.getGroup().getTeacher() != null)
                    ? s.getGroup().getTeacher().getFirstName() + " "
                      + s.getGroup().getTeacher().getLastName()
                    : null;
            Integer groupId = s.getGroup() != null ? s.getGroup().getIdGroup() : null;

            String title = groupName + " (" + s.getSessionStatus().name() + ")";

            events.add(new CalendarEventResponse(
                    "SESSION",
                    title,
                    s.getSessionDate(),
                    s.getTime(),
                    s.getSessionStatus().name(),
                    groupName,
                    teacherName,
                    groupId,
                    s.getIdSession()
            ));
        }

        // ── Zile libere din interval ──────────────────────────────────────────
        List<Holiday> holidays = holidayRepository
                .findByHolidayDateBetween(start, end);

        for (Holiday h : holidays) {
            String desc = h.getDescription() != null && !h.getDescription().isBlank()
                    ? h.getDescription() : "Zi liberă";
            events.add(new CalendarEventResponse(
                    "HOLIDAY",
                    "🎉 " + desc,
                    h.getHolidayDate(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            ));
        }

        // Sortare după dată + oră
        events.sort(Comparator
                .comparing(CalendarEventResponse::date)
                .thenComparing(e -> e.startTime() != null ? e.startTime().toString() : "00:00")
        );

        return events;
    }
}
