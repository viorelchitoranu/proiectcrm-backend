package com.springapp.proiectcrm.service;

import java.time.LocalDate;

public interface AttendanceArchiveService {
    /**
     * Arhivează + șterge toate attendance-urile unui copil dintr-o grupă,
     * pentru sesiuni cu sessionDate >= fromDate.
     *
     * @return numărul de rânduri arhivate (și șterse)
     */
    int archiveAndDeleteFutureAttendance(int childId, int groupId, LocalDate fromDate);
}
