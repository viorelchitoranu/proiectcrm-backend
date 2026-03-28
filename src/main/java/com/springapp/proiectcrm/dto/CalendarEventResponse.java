package com.springapp.proiectcrm.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO pentru evenimentele calendarului admin.
 * Compatibil cu formatul react-big-calendar (title, start, end, resource).
 *
 * Tipuri de evenimente:
 *   SESSION  → sesiune grupă (PLANNED, TAUGHT, CANCELED etc.)
 *   HOLIDAY  → zi liberă
 */
public record CalendarEventResponse(
        String  type,           // "SESSION" | "HOLIDAY"
        String  title,          // titlul afișat în calendar
        LocalDate date,         // data evenimentului
        LocalTime startTime,    // ora de start (null pentru holidays)
        String  status,         // SessionStatus sau null
        String  groupName,      // numele grupei (null pentru holidays)
        String  teacherName,    // numele profesorului (null pentru holidays)
        Integer groupId,        // id grupă (pentru navigare, null pentru holidays)
        Integer sessionId       // id sesiune (null pentru holidays)
) {}
