package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.GroupStatsResponse;
import com.springapp.proiectcrm.model.GroupClass;
import com.springapp.proiectcrm.model.SessionStatus;
import com.springapp.proiectcrm.repository.ChildGroupRepository;
import com.springapp.proiectcrm.repository.GroupClassRepository;
import com.springapp.proiectcrm.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final GroupClassRepository groupClassRepository;
    private final ChildGroupRepository childGroupRepository;
    private final SessionRepository sessionRepository;

    public List<GroupStatsResponse> getGroupStats() {
        List<GroupClass> groups = groupClassRepository.findAll();

        return groups.stream()
                .map(this::mapToStats)
                .toList();
    }

    public GroupStatsResponse getGroupStats(int groupId) {
        GroupClass group = groupClassRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        return mapToStats(group);
    }

    private GroupStatsResponse mapToStats(GroupClass group) {
        long enrolledChildren = childGroupRepository.countByGroupAndActiveTrue(group);

        long totalSessions = sessionRepository.countByGroup(group);
        long taughtSessions = sessionRepository.countByGroupAndSessionStatus(group, SessionStatus.TAUGHT);

        long plannedSessions = sessionRepository.countByGroupAndSessionStatus(group, SessionStatus.PLANNED);

        long canceledSessions =
                sessionRepository.countByGroupAndSessionStatus(group, SessionStatus.CANCELED)
                        + sessionRepository.countByGroupAndSessionStatus(group, SessionStatus.CANCELED_HOLIDAY)
                        + sessionRepository.countByGroupAndSessionStatus(group, SessionStatus.CANCELED_MANUAL)
                        + sessionRepository.countByGroupAndSessionStatus(group, SessionStatus.NOT_STARTED_SKIPPED);

        return new GroupStatsResponse(
                group.getIdGroup(),
                group.getGroupName(),
                group.getCourse() != null ? group.getCourse().getName() : null,
                group.getSchool() != null ? group.getSchool().getName() : null,
                enrolledChildren,
                totalSessions,
                taughtSessions,
                canceledSessions,
                plannedSessions
        );
    }

    //generator de CSV, cu ; ca separator
    public String exportGroupStatsAsCsv() {
        StringBuilder sb = new StringBuilder();
        sb.append("groupId;groupName;courseName;schoolName;enrolledChildren;totalSessions;taughtSessions;canceledSessions;plannedSessions\n");

        for (GroupStatsResponse stats : getGroupStats()) {
            sb.append(stats.getGroupId()).append(";")
                    .append(escape(stats.getGroupName())).append(";")
                    .append(escape(stats.getCourseName())).append(";")
                    .append(escape(stats.getSchoolName())).append(";")
                    .append(stats.getEnrolledChildren()).append(";")
                    .append(stats.getTotalSessions()).append(";")
                    .append(stats.getTaughtSessions()).append(";")
                    .append(stats.getCanceledSessions()).append(";")
                    .append(stats.getPlannedSessions()).append("\n");
        }

        return sb.toString();
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace(";", ",").replace("\n", " ");
    }
}