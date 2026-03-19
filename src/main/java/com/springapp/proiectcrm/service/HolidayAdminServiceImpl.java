package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.*;
import com.springapp.proiectcrm.model.GroupClass;
import com.springapp.proiectcrm.model.Holiday;
import com.springapp.proiectcrm.model.Session;
import com.springapp.proiectcrm.model.SessionStatus;
import com.springapp.proiectcrm.repository.AttendanceRepository;
import com.springapp.proiectcrm.repository.HolidayRepository;
import com.springapp.proiectcrm.repository.SessionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HolidayAdminServiceImpl implements HolidayAdminService {

    private final HolidayRepository holidayRepository;
    private final SessionRepository sessionRepository;
    private final AttendanceRepository attendanceRepository;

    // -------------------------
    // APPLY
    @Override
    @Transactional
    public HolidayResponse createHoliday(HolidayCreateRequest request) {
        if (request == null || request.getDate() == null) {
            throw new IllegalArgumentException("Holiday date is required");
        }

        // 1) STRICT preview
        HolidayPreviewResponse preview = previewHoliday(request);
        if (preview.getBlockedCount() > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "STRICT: Nu pot aplica holiday. Există sesiuni blocate (NOT_PLANNED sau HAS_ATTENDANCE)."
            );
        }

        LocalDate date = request.getDate();

        // 2) Upsert holiday
        Holiday holiday = holidayRepository.findByHolidayDate(date)
                .orElseGet(() -> {
                    Holiday h = new Holiday();
                    h.setHolidayDate(date);
                    h.setDescription(request.getDescription());
                    return holidayRepository.save(h);
                });

        // update descriere
        if (request.getDescription() != null) {
            holiday.setDescription(request.getDescription());
            holiday = holidayRepository.save(holiday);
        }

        // 3) Apply: PLANNED -> CANCELED_HOLIDAY
        applyCancelHolidayOnDate(date);

        return new HolidayResponse(holiday.getIdHoliday(), holiday.getHolidayDate(), holiday.getDescription());
    }

    @Override
    @Transactional
    public List<HolidayResponse> createHolidayRange(HolidayRangeRequest request) {
        if (request == null) throw new IllegalArgumentException("Request is required");
        LocalDate start = request.getStartDate();
        LocalDate end = request.getEndDate();

        if (start == null || end == null || end.isBefore(start)) {
            throw new IllegalArgumentException("Interval de vacanță invalid.");
        }

        // 1) STRICT preview
        HolidayPreviewResponse preview = previewHolidayRange(request);
        if (preview.getBlockedCount() > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "STRICT: Nu pot aplica holiday range. Există sesiuni blocate (NOT_PLANNED sau HAS_ATTENDANCE)."
            );
        }

        // 2) Upsert holidays zi cu zi
        List<HolidayResponse> responses = new ArrayList<>();
        LocalDate current = start;

        while (!current.isAfter(end)) {
            LocalDate d = current;

            Holiday h = holidayRepository.findByHolidayDate(d)
                    .orElseGet(() -> {
                        Holiday nh = new Holiday();
                        nh.setHolidayDate(d);
                        nh.setDescription(request.getDescription());
                        return holidayRepository.save(nh);
                    });

            if (request.getDescription() != null) {
                h.setDescription(request.getDescription());
                h = holidayRepository.save(h);
            }

            responses.add(new HolidayResponse(h.getIdHoliday(), h.getHolidayDate(), h.getDescription()));
            current = current.plusDays(1);
        }

        // 3) Apply: PLANNED -> CANCELED_HOLIDAY in interval
        applyCancelHolidayInRange(start, end);

        return responses;
    }

    // -------------------------
    // PREVIEW
    @Override
    public HolidayPreviewResponse previewHoliday(HolidayCreateRequest request) {
        if (request == null || request.getDate() == null) {
            throw new IllegalArgumentException("Holiday date is required");
        }

        LocalDate date = request.getDate();
        List<Session> sessions = sessionRepository.findBySessionDate(date);

        List<HolidayAffectedSessionResponse> willCancel = new ArrayList<>();
        List<HolidayBlockedSessionResponse> blocked = new ArrayList<>();

        for (Session s : sessions) {
            PreviewRow row = mapPreviewRow(s);

            // STRICT blocks
            if (s.getSessionStatus() != SessionStatus.PLANNED) {
                blocked.add(new HolidayBlockedSessionResponse(
                        row.sessionId, row.sessionDate, row.time, row.status,
                        row.groupId, row.groupName, row.courseName, row.schoolName, row.teacherName,
                        "NOT_PLANNED"
                ));
                continue;
            }

            if (attendanceRepository.existsBySession(s)) {
                blocked.add(new HolidayBlockedSessionResponse(
                        row.sessionId, row.sessionDate, row.time, row.status,
                        row.groupId, row.groupName, row.courseName, row.schoolName, row.teacherName,
                        "HAS_ATTENDANCE"
                ));
                continue;
            }

            willCancel.add(new HolidayAffectedSessionResponse(
                    row.sessionId, row.sessionDate, row.time, row.status,
                    row.groupId, row.groupName, row.courseName, row.schoolName, row.teacherName
            ));
        }

        return new HolidayPreviewResponse(
                date,
                null,
                willCancel.size(),
                blocked.size(),
                willCancel,
                blocked
        );
    }

    @Override
    public HolidayPreviewResponse previewHolidayRange(HolidayRangeRequest request) {
        if (request == null) throw new IllegalArgumentException("Request is required");
        LocalDate start = request.getStartDate();
        LocalDate end = request.getEndDate();

        if (start == null || end == null || end.isBefore(start)) {
            throw new IllegalArgumentException("Interval de vacanță invalid.");
        }

        // preview strict - TOATE sesiunile din interval, indiferent de status
        List<Session> sessions = sessionRepository.findBySessionDateBetween(start, end);

        List<HolidayAffectedSessionResponse> willCancel = new ArrayList<>();
        List<HolidayBlockedSessionResponse> blocked = new ArrayList<>();

        for (Session s : sessions) {
            PreviewRow row = mapPreviewRow(s);

            if (s.getSessionStatus() != SessionStatus.PLANNED) {
                blocked.add(new HolidayBlockedSessionResponse(
                        row.sessionId, row.sessionDate, row.time, row.status,
                        row.groupId, row.groupName, row.courseName, row.schoolName, row.teacherName,
                        "NOT_PLANNED"
                ));
                continue;
            }

            if (attendanceRepository.existsBySession(s)) {
                blocked.add(new HolidayBlockedSessionResponse(
                        row.sessionId, row.sessionDate, row.time, row.status,
                        row.groupId, row.groupName, row.courseName, row.schoolName, row.teacherName,
                        "HAS_ATTENDANCE"
                ));
                continue;
            }

            willCancel.add(new HolidayAffectedSessionResponse(
                    row.sessionId, row.sessionDate, row.time, row.status,
                    row.groupId, row.groupName, row.courseName, row.schoolName, row.teacherName
            ));
        }

        return new HolidayPreviewResponse(
                start,
                end,
                willCancel.size(),
                blocked.size(),
                willCancel,
                blocked
        );
    }

    // -------------------------
    // endpoints
    @Override
    public List<HolidayResponse> getAllHolidays() {
        return holidayRepository.findAll().stream()
                .map(h -> new HolidayResponse(h.getIdHoliday(), h.getHolidayDate(), h.getDescription()))
                .toList();
    }

    @Override
    public List<HolidayAffectedSessionResponse> getAffectedSessions(int holidayId) {
        Holiday holiday = holidayRepository.findById(holidayId)
                .orElseThrow(() -> new IllegalArgumentException("Holiday not found"));

        LocalDate date = holiday.getHolidayDate();
        List<Session> sessions = sessionRepository.findBySessionDate(date);

        return sessions.stream().map(s -> {
            PreviewRow row = mapPreviewRow(s);
            return new HolidayAffectedSessionResponse(
                    row.sessionId, row.sessionDate, row.time, row.status,
                    row.groupId, row.groupName, row.courseName, row.schoolName, row.teacherName
            );
        }).toList();
    }

    @Override
    @Transactional
    public int applyHolidayToSessions(int holidayId, HolidayCancelSessionsRequest request) {
        Holiday holiday = holidayRepository.findById(holidayId)
                .orElseThrow(() -> new IllegalArgumentException("Holiday not found"));

        if (request == null || request.getSessionIds() == null || request.getSessionIds().isEmpty()) {
            return 0;
        }

        LocalDate holidayDate = holiday.getHolidayDate();
        List<Session> sessions = sessionRepository.findAllById(request.getSessionIds());

        // daca exista vreo sesiune invalidă => 409
        for (Session s : sessions) {
            if (!holidayDate.equals(s.getSessionDate())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "STRICT: Ai trimis sesiuni din altă zi.");
            }
            if (s.getSessionStatus() != SessionStatus.PLANNED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "STRICT: Există sesiuni care nu sunt PLANNED.");
            }
            if (attendanceRepository.existsBySession(s)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "STRICT: Există sesiuni cu attendance.");
            }
        }

        for (Session s : sessions) {
            s.setSessionStatus(SessionStatus.CANCELED_HOLIDAY);
        }
        sessionRepository.saveAll(sessions);

        return sessions.size();
    }

    // -------------------------
    // Helpers apply
    private void applyCancelHolidayOnDate(LocalDate date) {
        List<Session> planned = sessionRepository.findBySessionDateAndSessionStatus(date, SessionStatus.PLANNED);
        for (Session s : planned) {
            s.setSessionStatus(SessionStatus.CANCELED_HOLIDAY);
        }
        sessionRepository.saveAll(planned);
    }

    private void applyCancelHolidayInRange(LocalDate start, LocalDate end) {
        List<Session> planned = sessionRepository.findBySessionDateBetweenAndSessionStatus(start, end, SessionStatus.PLANNED);
        for (Session s : planned) {
            s.setSessionStatus(SessionStatus.CANCELED_HOLIDAY);
        }
        sessionRepository.saveAll(planned);
    }

    // -------------------------
    // Mapping helper
    private static class PreviewRow {
        int sessionId;
        LocalDate sessionDate;
        java.time.LocalTime time;
        SessionStatus status;

        Integer groupId;
        String groupName;
        String courseName;
        String schoolName;
        String teacherName;
    }

    private PreviewRow mapPreviewRow(Session s) {
        PreviewRow r = new PreviewRow();
        r.sessionId = s.getIdSession();
        r.sessionDate = s.getSessionDate();
        r.time = s.getTime();
        r.status = s.getSessionStatus();

        GroupClass g = s.getGroup();
        r.groupId = (g != null) ? g.getIdGroup() : null;
        r.groupName = (g != null) ? g.getGroupName() : null;
        r.courseName = (g != null && g.getCourse() != null) ? g.getCourse().getName() : null;
        r.schoolName = (g != null && g.getSchool() != null) ? g.getSchool().getName() : null;
        r.teacherName = (g != null && g.getTeacher() != null)
                ? g.getTeacher().getLastName() + " " + g.getTeacher().getFirstName()
                : null;

        return r;
    }
}
