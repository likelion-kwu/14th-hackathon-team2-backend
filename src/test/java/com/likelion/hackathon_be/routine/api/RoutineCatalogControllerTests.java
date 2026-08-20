package com.likelion.hackathon_be.routine.api;

import com.likelion.hackathon_be.common.error.GlobalExceptionHandler;
import com.likelion.hackathon_be.routine.application.RoutineCatalogService;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.RoutineRecommendationResponse;
import com.likelion.hackathon_be.routine.dto.VerificationObjectResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoutineCatalogControllerTests {

    private RoutineCatalogService routineCatalogService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        routineCatalogService = mock(RoutineCatalogService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new VerificationObjectController(routineCatalogService),
                        new RoutineRecommendationController(routineCatalogService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void verificationObjectsFollowApiResponseContract() throws Exception {
        when(routineCatalogService.getVerificationObjects()).thenReturn(List.of(
                new VerificationObjectResponse("CUP", "컵"),
                new VerificationObjectResponse("COSMETIC_CONTAINER", "화장품 용기")
        ));

        mockMvc.perform(get("/api/v1/verification-objects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("CUP"))
                .andExpect(jsonPath("$.data[0].name").value("컵"))
                .andExpect(jsonPath("$.data[1].code").value("COSMETIC_CONTAINER"))
                .andExpect(jsonPath("$.meta").doesNotExist());
    }

    @Test
    void recommendationsReturnRequestedCategoryAndActualCount() throws Exception {
        when(routineCatalogService.getRecommendations(RoutineCategory.SKIN)).thenReturn(List.of(
                new RoutineRecommendationResponse(
                        "SKIN_SUNSCREEN",
                        "SKIN",
                        "외출 전 선크림 바르기",
                        "COSMETIC_CONTAINER"
                )
        ));

        mockMvc.perform(get("/api/v1/routine-recommendations").param("category", "SKIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("SKIN_SUNSCREEN"))
                .andExpect(jsonPath("$.data[0].category").value("SKIN"))
                .andExpect(jsonPath("$.data[0].recommendedVerificationObject")
                        .value("COSMETIC_CONTAINER"))
                .andExpect(jsonPath("$.meta.count").value(1));
    }

    @Test
    void missingRecommendationCategoryReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/routine-recommendations"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ROUTINE_CATEGORY"));

        verifyNoInteractions(routineCatalogService);
    }

    @Test
    void invalidRecommendationCategoryReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/routine-recommendations").param("category", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ROUTINE_CATEGORY"));

        verifyNoInteractions(routineCatalogService);
    }
}
