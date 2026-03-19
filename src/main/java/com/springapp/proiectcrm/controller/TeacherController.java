package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.dto.*;
import com.springapp.proiectcrm.exception.BusinessException;
import com.springapp.proiectcrm.exception.ErrorCode;
import com.springapp.proiectcrm.model.AttendanceStatus;
import com.springapp.proiectcrm.service.TeacherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherController {

    private final TeacherService teacherService;

    @GetMapping
    public List<TeacherGroupSummaryResponse> getTeacherGroups(Principal principal) {
        return teacherService.getTeacherGroups(currentUserEmail(principal));
    }

    @GetMapping("/groups/active")
    public List<TeacherGroupSummaryResponse> getActiveTeacherGroups(Principal principal) {
        return teacherService.getActiveTeacherGroups(currentUserEmail(principal));
    }

    @GetMapping("/groups/finished")
    public List<TeacherGroupSummaryResponse> getFinishedTeacherGroups(Principal principal) {
        return teacherService.getFinishedTeacherGroups(currentUserEmail(principal));
    }

    @GetMapping("/groups/{groupId}/sessions")
    public List<TeacherSessionSummaryResponse> getGroupSessions(
            Principal principal,
            @PathVariable int groupId
    ) {
        return teacherService.getGroupSessions(currentUserEmail(principal), groupId);
    }

    @GetMapping("/groups/{groupId}/sessions/{sessionId}/attendance")
    public List<TeacherAttendanceRowResponse> getSessionAttendance(
            Principal principal,
            @PathVariable int groupId,
            @PathVariable int sessionId
    ) {
        return teacherService.getSessionAttendance(
                currentUserEmail(principal),
                groupId,
                sessionId
        );
    }

    @PostMapping("/groups/{groupId}/sessions/{sessionId}/attendance")
    public List<TeacherAttendanceRowResponse> updateSessionAttendance(
            Principal principal,
            @PathVariable int groupId,
            @PathVariable int sessionId,
            @RequestBody TeacherAttendanceUpdateRequest request
    ) {
        return teacherService.updateSessionAttendance(
                currentUserEmail(principal),
                groupId,
                sessionId,
                request
        );
    }

    @GetMapping("/requests")
    public List<TeacherParentRequestResponse> getParentRequests(
            Principal principal,
            @RequestParam(required = false) AttendanceStatus type
    ) {
        return teacherService.getParentRequests(
                currentUserEmail(principal),
                type
        );
    }

    @PostMapping("/requests/{attendanceId}/allocate-recovery")
    public void allocateRecovery(
            Principal principal,
            @PathVariable int attendanceId,
            @RequestBody TeacherAllocateRecoveryRequest request
    ) {
        teacherService.allocateRecovery(
                currentUserEmail(principal),
                attendanceId,
                request
        );
    }

    @GetMapping("/requests/{attendanceId}/recovery-target-sessions")
    public List<TeacherRecoveryTargetSessionResponse> getRecoveryTargetSessions(
            Principal principal,
            @PathVariable int attendanceId
    ) {
        return teacherService.getRecoveryTargetSessions(
                currentUserEmail(principal),
                attendanceId
        );
    }

    @PostMapping("/requests/{attendanceId}/confirm-cancel")
    public void confirmCancel(
            Principal principal,
            @PathVariable int attendanceId
    ) {
        teacherService.confirmCancelRequest(
                currentUserEmail(principal),
                attendanceId
        );
    }

    @PostMapping("/groups/{groupId}/start")
    public StartGroupResponse startGroup(
            Principal principal,
            @PathVariable int groupId
    ) {
        return teacherService.startGroup(
                currentUserEmail(principal),
                groupId
        );
    }

    @PutMapping("/password")
    public void changeOwnPassword(
            Principal principal,
            @RequestBody TeacherChangeOwnPasswordRequest request
    ) {
        teacherService.changeOwnPassword(
                currentUserEmail(principal),
                request
        );
    }

    private String currentUserEmail(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new BusinessException(
                    ErrorCode.ACCESS_DENIED,
                    "Utilizator neautentificat."
            );
        }
        return principal.getName();
    }
}