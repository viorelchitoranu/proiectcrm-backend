package com.springapp.proiectcrm.dto;

import lombok.Data;

import java.util.List;

@Data
public class HolidayCancelSessionsRequest {
    private List<Integer> sessionIds;
}
