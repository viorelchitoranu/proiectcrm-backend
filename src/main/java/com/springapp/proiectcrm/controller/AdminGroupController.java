package com.springapp.proiectcrm.controller;

import com.springapp.proiectcrm.dto.*;
import com.springapp.proiectcrm.model.GroupClass;
import com.springapp.proiectcrm.model.Session;
import com.springapp.proiectcrm.repository.GroupClassRepository;
import com.springapp.proiectcrm.repository.SessionRepository;
import com.springapp.proiectcrm.service.GroupAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/groups")
@RequiredArgsConstructor

public class AdminGroupController {
    private final GroupAdminService groupAdminService;
    private final GroupClassRepository groupClassRepository;
    private final SessionRepository sessionRepository;


    @PostMapping
    public GroupAdminResponse createGroup(@RequestBody GroupCreateRequest request) {
        return groupAdminService.createGroupWithSessions(request);
    }

    @GetMapping
    public List<GroupAdminResponse> getAllGroups() {
        return groupAdminService.getAllGroups();
    }

    @GetMapping("/{groupId}")
    public GroupAdminResponse getGroup(@PathVariable int groupId) {
        return groupAdminService.getGroup(groupId);
    }

    @GetMapping("/by-course/{courseId}")
    public List<GroupAdminResponse> getGroupsByCourse(@PathVariable int courseId) {
        return groupAdminService.getGroupsByCourse(courseId);
    }

    @PutMapping("/{groupId}")
    public GroupAdminResponse updateGroup(@PathVariable int groupId, @RequestBody GroupUpdateRequest request) { // Update.

        return groupAdminService.updateGroup(groupId, request);
    }

    @PostMapping("/{groupId}/start")
    public GroupAdminResponse startGroup(@PathVariable int groupId, @RequestBody(required = false) GroupStartRequest request) { // Start.
        return groupAdminService.startGroup(groupId, request);
    }


    @GetMapping("/{groupId}/sessions")
    public List<AdminSessionSummaryResponse> getGroupSessions(
            @PathVariable int groupId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate
    ) {
        GroupClass group = groupClassRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        List<Session> sessions = (fromDate == null)
                ? sessionRepository.findByGroupOrderBySessionDateAsc(group)
                : sessionRepository.findByGroupAndSessionDateGreaterThanEqualOrderBySessionDateAsc(group, fromDate);


        return sessions.stream().map(this::mapSession).toList();
    }


    private AdminSessionSummaryResponse mapSession(Session s) {
        return new AdminSessionSummaryResponse(
                s.getIdSession(),
                s.getSessionDate(),
                s.getTime(),
                s.getSessionStatus(),
                s.getSessionType(),
                s.getName()
        );
    }


    @PostMapping("/{groupId}/stop")
    public GroupAdminResponse stopGroup(
            @PathVariable int groupId,
            @RequestBody(required = false) GroupStopRequest request
    ) {
        return groupAdminService.stopGroup(groupId, request);
    }

    @PostMapping("/{groupId}/purge-non-taught")
    public void purgeNonTaught(@PathVariable int groupId) {
        groupAdminService.purgeNonTaughtSessions(groupId);
    }

    @DeleteMapping("/{groupId}/delete-safe")
    public GroupDeleteSafeResponse deleteGroupSafe(@PathVariable int groupId) {
        return groupAdminService.deleteGroupSafe(groupId);
    }


}
