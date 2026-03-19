package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.model.*;
import com.springapp.proiectcrm.repository.AttendanceArchiveRepository;
import com.springapp.proiectcrm.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceArchiveServiceImpl implements AttendanceArchiveService {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceArchiveRepository attendanceArchiveRepository;

    @Override
    @Transactional
    public int archiveAndDeleteFutureAttendance(int childId, int groupId, LocalDate fromDate) {

        // attendance-urile care trebuie mutate
        List<Attendance> toArchive = attendanceRepository
                .findByChild_IdChildAndSession_Group_IdGroupAndSession_SessionDateGreaterThanEqual(
                        childId, groupId, fromDate
                );

        if (toArchive.isEmpty()) {
            return 0;
        }

        // 2) mapare Attendance -> AttendanceArchive
        LocalDateTime now = LocalDateTime.now();

        List<AttendanceArchive> archives = toArchive.stream().map(a -> {
            Session s = a.getSession();
            GroupClass g = s.getGroup();
            Child c = a.getChild();
            User p = (c != null) ? c.getParent() : null;

            AttendanceArchive ar = new AttendanceArchive();

            // ids originale
            ar.setOriginalAttendanceId(a.getIdAttendance());
            ar.setOriginalSessionId(s != null ? s.getIdSession() : null);
            ar.setOriginalGroupId(g != null ? g.getIdGroup() : null);

            // group/session info
            ar.setGroupName(g != null ? g.getGroupName() : null);
            ar.setCourseName(g != null && g.getCourse() != null ? g.getCourse().getName() : null);
            ar.setSchoolName(g != null && g.getSchool() != null ? g.getSchool().getName() : null);
            ar.setTeacherName(g != null && g.getTeacher() != null
                    ? g.getTeacher().getLastName() + " " + g.getTeacher().getFirstName()
                    : null);

            ar.setSessionDate(s != null ? s.getSessionDate() : null);
            ar.setSessionTime(s != null ? s.getTime() : null);
            ar.setSessionStatus(s != null ? s.getSessionStatus() : null);
            ar.setSessionType(s != null ? s.getSessionType() : null);

            // child/parent info
            ar.setChildId(c != null ? c.getIdChild() : null);
            ar.setChildFirstName(c != null ? c.getChildFirstName() : null);
            ar.setChildLastName(c != null ? c.getChildLastName() : null);

            ar.setParentId(p != null ? p.getIdUser() : null);
            ar.setParentName(p != null ? p.getLastName() + " " + p.getFirstName() : null);
            ar.setParentEmail(p != null ? p.getEmail() : null);
            ar.setParentPhone(p != null ? p.getPhone() : null);

            // attendance original
            ar.setAttendanceStatus(a.getStatus());
            ar.setNota(a.getNota());
            ar.setCreatedAt(a.getCreatedAt());
            ar.setUpdatedAt(a.getUpdatedAt());
            ar.setRecovery(a.isRecovery());
            ar.setRecoveryForSessionId(a.getRecoveryForSessionId());

            // metadata
            ar.setArchivedAt(now);

            return ar;
        }).toList();

        // 3) insert arhiva
        attendanceArchiveRepository.saveAll(archives);

        // 4) delete original
        attendanceRepository.deleteAll(toArchive);

        return archives.size();
    }
}