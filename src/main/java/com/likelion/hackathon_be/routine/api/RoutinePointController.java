package com.likelion.hackathon_be.routine.api;

import com.likelion.hackathon_be.common.api.ApiResponse;
import com.likelion.hackathon_be.routine.application.RoutinePointService;
import com.likelion.hackathon_be.routine.dto.PointClaimResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/daily-routines/{dailyRoutineId}/point-claim")
public class RoutinePointController {

    private final RoutinePointService routinePointService;

    public RoutinePointController(RoutinePointService routinePointService) {
        this.routinePointService = routinePointService;
    }

    @PostMapping
    public ApiResponse<PointClaimResponse> claimPoint(@PathVariable Long dailyRoutineId) {
        return ApiResponse.of(routinePointService.claimPoint(dailyRoutineId));
    }
}
