package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.*;

import java.util.List;

public interface ParentService {

    List<ChildSummaryResponse> getChildren(String authenticatedEmail);

    List<ChildEnrollmentResponse> getChildEnrollments(String authenticatedEmail, int childId);

    ChildGroupScheduleResponse getChildGroupSchedule(String authenticatedEmail, int childId, int groupId);

    ParentSessionActionResponse cancelChildSession(
            String authenticatedEmail,
            int childId,
            int sessionId,
            ParentSessionActionRequest request
    );

    ParentSessionActionResponse requestRecoveryForChildSession(
            String authenticatedEmail,
            int childId,
            int sessionId,
            ParentSessionActionRequest request
    );

    ParentPasswordUpdateResponse updateParentPassword(
            String authenticatedEmail,
            ParentPasswordUpdateRequest request
    );
}
