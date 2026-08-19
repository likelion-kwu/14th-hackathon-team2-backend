package com.likelion.hackathon_be.routine.api;

import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.error.GlobalExceptionHandler;
import com.likelion.hackathon_be.routine.application.PhotoMissionService;
import com.likelion.hackathon_be.routine.dto.PhotoMissionDetailResponse;
import com.likelion.hackathon_be.routine.dto.PhotoMissionResponse;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PhotoMissionControllerTests {

    private PhotoMissionService photoMissionService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        photoMissionService = mock(PhotoMissionService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PhotoMissionController(photoMissionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void preparesPhotoMissionUsingApiContract() throws Exception {
        when(photoMissionService.preparePhotoMission(406L)).thenReturn(new PhotoMissionResponse(
                406L,
                "CUP",
                new PhotoMissionDetailResponse(8L, "THUMBS_UP", "컵과 함께 엄지척 해주세요."),
                OffsetDateTime.parse("2026-08-17T20:01:00+09:00")
        ));

        mockMvc.perform(post("/api/v1/daily-routines/{dailyRoutineId}/photo-mission", 406L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyRoutineId").value(406))
                .andExpect(jsonPath("$.data.verificationObject").value("CUP"))
                .andExpect(jsonPath("$.data.mission.templateId").value(8))
                .andExpect(jsonPath("$.data.mission.gestureCode").value("THUMBS_UP"))
                .andExpect(jsonPath("$.data.mission.instruction").value("컵과 함께 엄지척 해주세요."))
                .andExpect(jsonPath("$.data.actualEndAtExclusive").value("2026-08-17T20:01:00+09:00"))
                .andExpect(jsonPath("$.meta").doesNotExist());

        verify(photoMissionService).preparePhotoMission(406L);
    }

    @Test
    void mapsOwnedLookupFailureToDailyRoutineNotFound() throws Exception {
        when(photoMissionService.preparePhotoMission(999L))
                .thenThrow(new BusinessException(ErrorCode.DAILY_ROUTINE_NOT_FOUND));

        mockMvc.perform(post("/api/v1/daily-routines/{dailyRoutineId}/photo-mission", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DAILY_ROUTINE_NOT_FOUND"));
    }

    @Test
    void mapsMissingActiveTemplateToMissionNotPrepared() throws Exception {
        when(photoMissionService.preparePhotoMission(406L))
                .thenThrow(new BusinessException(ErrorCode.PHOTO_MISSION_NOT_PREPARED));

        mockMvc.perform(post("/api/v1/daily-routines/{dailyRoutineId}/photo-mission", 406L))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHOTO_MISSION_NOT_PREPARED"));
    }
}
