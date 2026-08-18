package com.likelion.hackathon_be.routine.api;

import com.likelion.hackathon_be.common.api.ApiResponse;
import com.likelion.hackathon_be.routine.application.RoutineCatalogService;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.RoutineRecommendationResponse;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/routine-recommendations")
public class RoutineRecommendationController {

    private final RoutineCatalogService routineCatalogService;

    public RoutineRecommendationController(RoutineCatalogService routineCatalogService) {
        this.routineCatalogService = routineCatalogService;
    }

    @GetMapping
    public ApiResponse<List<RoutineRecommendationResponse>> getRecommendations(
            @NotNull @RequestParam RoutineCategory category
    ) {
        List<RoutineRecommendationResponse> recommendations = routineCatalogService.getRecommendations(category);
        return ApiResponse.of(recommendations, Map.of("count", recommendations.size()));
    }
}
