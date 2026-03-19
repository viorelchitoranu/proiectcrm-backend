package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.*;

import java.util.List;

public interface GroupAdminService {
    GroupAdminResponse createGroupWithSessions(GroupCreateRequest request);
    GroupAdminResponse updateGroup(int groupId, GroupUpdateRequest request);
    List<GroupAdminResponse> getAllGroups();
    GroupAdminResponse getGroup(int groupId);
    GroupAdminResponse startGroup(int groupId, GroupStartRequest request);
    List<GroupAdminResponse> getGroupsByCourse(int courseId);
    List<SessionAdminResponse> getGroupSessions(int groupId);
    GroupAdminResponse stopGroup(int groupId, GroupStopRequest request);
    void purgeNonTaughtSessions(int groupId);
    GroupDeleteSafeResponse deleteGroupSafe(int groupId);

}
