package com.likelion.hackathon_be.routine.api;

import com.likelion.hackathon_be.common.api.ApiResponse;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.routine.application.RoutineCatalogService;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.RoutineRecommendationResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/routine-recommendations")
public class RoutineRecommendationController {

    private final RoutineCatalogService routineCatalogService;

    public RoutineRecommendationController(RoutineCatalogService routineCatalogService) {
        this.routineCatalogService = routineCatalogService;
    }

    @GetMapping
    public ApiResponse<List<RoutineRecommendationResponse>> getRecommendations(
            @RequestParam(required = false) String category
    ) {
        List<RoutineRecommendationResponse> recommendations = routineCatalogService.getRecommendations(parseCategory(category));
        return ApiResponse.of(recommendations, Map.of("count", recommendations.size()));
    }

    private RoutineCategory parseCategory(String category) {
        if (category == null || category.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_ROUTINE_CATEGORY);
        }
        try {
            return RoutineCategory.valueOf(category);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_ROUTINE_CATEGORY);
        }
    }
}
