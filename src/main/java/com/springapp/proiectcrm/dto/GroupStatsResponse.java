package com.springapp.proiectcrm.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GroupStatsResponse {
    private int groupId;
    private String groupName;
    private String courseName;
    private String schoolName;

    private long enrolledChildren;
    private long totalSessions;
    private long taughtSessions;
    private long canceledSessions;
    private long plannedSessions;
}
