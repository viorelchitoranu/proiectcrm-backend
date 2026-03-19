package com.springapp.proiectcrm.service;

import com.springapp.proiectcrm.dto.*;

import java.util.List;

public interface HolidayAdminService {

    HolidayResponse createHoliday(HolidayCreateRequest request);
    List<HolidayResponse> createHolidayRange(HolidayRangeRequest request);

    List<HolidayResponse> getAllHolidays();

    List<HolidayAffectedSessionResponse> getAffectedSessions(int holidayId);

    int applyHolidayToSessions(int holidayId, HolidayCancelSessionsRequest request);

    HolidayPreviewResponse previewHoliday(HolidayCreateRequest request);
    HolidayPreviewResponse previewHolidayRange(HolidayRangeRequest request);

}
