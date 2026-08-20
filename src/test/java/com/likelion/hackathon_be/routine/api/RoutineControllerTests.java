package com.likelion.hackathon_be.routine.api;

import com.likelion.hackathon_be.common.error.GlobalExceptionHandler;
import com.likelion.hackathon_be.routine.application.RoutineService;
import com.likelion.hackathon_be.routine.dto.RoutineResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoutineControllerTests {

    private RoutineService routineService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        routineService = mock(RoutineService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RoutineController(routineService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listReturnsRoutineContractIncludingEffectiveState() throws Exception {
        when(routineService.getRoutines()).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/v1/routines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(101))
                .andExpect(jsonPath("$.data[0].daysOfWeek[0]").value("MON"))
                .andExpect(jsonPath("$.data[0].effectiveFrom").value("2026-08-19"))
                .andExpect(jsonPath("$.data[0].appliedToCurrentServiceDate").value(true));
    }

    @Test
    void createReturns201AndDoesNotAcceptClientUserId() throws Exception {
        when(routineService.createRoutine(any())).thenReturn(response());

        mockMvc.perform(post("/api/v1/routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 9999,
                                  "category": "WELL_BEING",
                                  "content": "물 마시기",
                                  "startTime": "13:00",
                                  "endTime": "14:00",
                                  "repeatType": "DAYS_OF_WEEK",
                                  "daysOfWeek": ["MON"],
                                  "verificationObject": "CUP"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(101))
                .andExpect(jsonPath("$.data.effectiveFrom").value("2026-08-19"));

        verify(routineService).createRoutine(any());
    }

    @Test
    void invalidLocalTimeReturnsValidation400BeforeService() throws Exception {
        mockMvc.perform(post("/api/v1/routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "WELL_BEING",
                                  "content": "물 마시기",
                                  "startTime": "13:00",
                                  "endTime": "24:00",
                                  "repeatType": "DAILY",
                                  "daysOfWeek": [],
                                  "verificationObject": "CUP"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(routineService, never()).createRoutine(any());
    }

    @Test
    void blankContentReturnsValidation400BeforeService() throws Exception {
        mockMvc.perform(post("/api/v1/routines")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "WELL_BEING",
                                  "content": "   ",
                                  "startTime": "13:00",
                                  "endTime": "14:00",
                                  "repeatType": "DAILY",
                                  "daysOfWeek": [],
                                  "verificationObject": "CUP"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(routineService, never()).createRoutine(any());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/routines/{routineId}", 101L))
                .andExpect(status().isNoContent());

        verify(routineService).deleteRoutine(101L);
    }

    private RoutineResponse response() {
        return new RoutineResponse(
                101L,
                "WELL_BEING",
                "물 마시기",
                null,
                LocalTime.of(13, 0),
                LocalTime.of(14, 0),
                "DAYS_OF_WEEK",
                List.of("MON"),
                "CUP",
                LocalDate.of(2026, 8, 19),
                true,
                OffsetDateTime.parse("2026-08-19T12:00:00+09:00"),
                OffsetDateTime.parse("2026-08-19T12:00:00+09:00")
        );
    }
}
