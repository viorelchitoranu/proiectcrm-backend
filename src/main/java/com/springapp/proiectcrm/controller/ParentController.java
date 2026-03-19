package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.dto.*;
import com.springapp.proiectcrm.exception.BusinessException;
import com.springapp.proiectcrm.exception.ErrorCode;
import com.springapp.proiectcrm.service.ParentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/parent")
@RequiredArgsConstructor
public class ParentController {

    private final ParentService parentService;

    @GetMapping("/children")
    public List<ChildSummaryResponse> getChildren(Principal principal) {
        return parentService.getChildren(currentUserEmail(principal));
    }

    @GetMapping("/children/{childId}/enrollments")
    public List<ChildEnrollmentResponse> getChildEnrollments(
            Principal principal,
            @PathVariable int childId
    ) {
        return parentService.getChildEnrollments(currentUserEmail(principal), childId);
    }

    @GetMapping("/children/{childId}/groups/{groupId}/schedule")
    public ChildGroupScheduleResponse getChildGroupSchedule(
            Principal principal,
            @PathVariable int childId,
            @PathVariable int groupId
    ) {
        return parentService.getChildGroupSchedule(currentUserEmail(principal), childId, groupId);
    }

    @PostMapping("/children/{childId}/sessions/{sessionId}/cancel")
    public ParentSessionActionResponse cancelChildSession(
            Principal principal,
            @PathVariable int childId,
            @PathVariable int sessionId,
            @RequestBody(required = false) ParentSessionActionRequest request
    ) {
        return parentService.cancelChildSession(
                currentUserEmail(principal),
                childId,
                sessionId,
                request
        );
    }

    @PostMapping("/children/{childId}/sessions/{sessionId}/request-recovery")
    public ParentSessionActionResponse requestRecoveryForChildSession(
            Principal principal,
            @PathVariable int childId,
            @PathVariable int sessionId,
            @RequestBody(required = false) ParentSessionActionRequest request
    ) {
        return parentService.requestRecoveryForChildSession(
                currentUserEmail(principal),
                childId,
                sessionId,
                request
        );
    }

    @PutMapping("/password")
    public ParentPasswordUpdateResponse updateParentPassword(
            Principal principal,
           @Valid @RequestBody ParentPasswordUpdateRequest request
    ) {
        return parentService.updateParentPassword(currentUserEmail(principal), request);
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