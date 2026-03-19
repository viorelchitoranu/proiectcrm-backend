package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.*;
import com.springapp.proiectcrm.model.AttendanceStatus;

import java.util.List;

public interface TeacherService {


    List<TeacherGroupSummaryResponse> getTeacherGroups(String authenticatedEmail);

    List<TeacherGroupSummaryResponse> getActiveTeacherGroups(String authenticatedEmail);

    List<TeacherGroupSummaryResponse> getFinishedTeacherGroups(String authenticatedEmail);

    List<TeacherSessionSummaryResponse> getGroupSessions(String authenticatedEmail, int groupId);

    List<TeacherAttendanceRowResponse> getSessionAttendance(
            String authenticatedEmail,
            int groupId,
            int sessionId
    );

    List<TeacherAttendanceRowResponse> updateSessionAttendance(
            String authenticatedEmail,
            int groupId,
            int sessionId,
            TeacherAttendanceUpdateRequest request
    );

    List<TeacherParentRequestResponse> getParentRequests(
            String authenticatedEmail,
            AttendanceStatus type
    );

    void allocateRecovery(
            String authenticatedEmail,
            int attendanceId,
            TeacherAllocateRecoveryRequest request
    );

    List<TeacherRecoveryTargetSessionResponse> getRecoveryTargetSessions(
            String authenticatedEmail,
            int attendanceId
    );

    void confirmCancelRequest(
            String authenticatedEmail,
            int attendanceId
    );

    StartGroupResponse startGroup(
            String authenticatedEmail,
            int groupId
    );

    void changeOwnPassword(
            String authenticatedEmail,
            TeacherChangeOwnPasswordRequest request
    );
}