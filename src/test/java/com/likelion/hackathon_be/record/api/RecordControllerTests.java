package com.likelion.hackathon_be.record.api;

import com.likelion.hackathon_be.record.application.RecordService;
import com.likelion.hackathon_be.record.dto.RecordDayResponse;
import com.likelion.hackathon_be.record.dto.RecordPeriodResponse;
import com.likelion.hackathon_be.record.dto.RecordResponse;
import com.likelion.hackathon_be.record.dto.RecordRoutineResponse;
import com.likelion.hackathon_be.record.dto.RecordSummaryResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecordControllerTests {

    private RecordService recordService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        recordService = mock(RecordService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new RecordController(recordService)).build();
    }

    @Test
    void returnsMonthlyCalendarSourceContractAndPassesExactRange() throws Exception {
        LocalDate fromDate = LocalDate.of(2026, 8, 1);
        LocalDate toDate = LocalDate.of(2026, 8, 31);
        when(recordService.getRecords(fromDate, toDate)).thenReturn(response(fromDate, toDate));

        mockMvc.perform(get("/api/v1/records")
                        .param("fromDate", "2026-08-01")
                        .param("toDate", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period.fromDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data.period.toDate").value("2026-08-31"))
                .andExpect(jsonPath("$.data.summary.totalSuccessDays").value(12))
                .andExpect(jsonPath("$.data.days[0].serviceDate").value("2026-08-19"))
                .andExpect(jsonPath("$.data.days[0].dayStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.days[0].completedCount").value(1))
                .andExpect(jsonPath("$.data.days[0].totalCount").value(3))
                .andExpect(jsonPath("$.data.days[0].routines[0].dailyRoutineId").value(405))
                .andExpect(jsonPath("$.data.days[0].routines[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.days[0].routines[0].verificationType").value("PHOTO"))
                .andExpect(jsonPath("$.data.days[1].serviceDate").value("2026-08-18"))
                .andExpect(jsonPath("$.data.days[1].dayStatus").value("SUCCESS"))
                .andExpect(jsonPath("$.data.days[1].completedCount").value(2))
                .andExpect(jsonPath("$.data.days[1].totalCount").value(2));

        verify(recordService).getRecords(fromDate, toDate);
    }

    private RecordResponse response(LocalDate fromDate, LocalDate toDate) {
        return new RecordResponse(
                new RecordPeriodResponse(fromDate, toDate),
                new RecordSummaryResponse(40, 32, 80, 20, 12, 12, 4, 10),
                List.of(
                        new RecordDayResponse(
                                LocalDate.of(2026, 8, 19),
                                "IN_PROGRESS",
                                1,
                                3,
                                List.of(new RecordRoutineResponse(
                                        405L,
                                        101L,
                                        "선크림 바르기",
                                        "COMPLETED",
                                        "PHOTO"
                                ))
                        ),
                        new RecordDayResponse(
                                LocalDate.of(2026, 8, 18),
                                "SUCCESS",
                                2,
                                2,
                                List.of()
                        )
                )
        );
    }
}
