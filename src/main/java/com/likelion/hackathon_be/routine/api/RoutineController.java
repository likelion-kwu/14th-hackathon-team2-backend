package com.likelion.hackathon_be.routine.api;

import com.likelion.hackathon_be.common.api.ApiResponse;
import com.likelion.hackathon_be.routine.application.RoutineService;
import com.likelion.hackathon_be.routine.dto.CreateRoutineRequest;
import com.likelion.hackathon_be.routine.dto.RoutineResponse;
import com.likelion.hackathon_be.routine.dto.UpdateRoutineRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/routines")
public class RoutineController {

    private final RoutineService routineService;

    public RoutineController(RoutineService routineService) {
        this.routineService = routineService;
    }

    @GetMapping
    public ApiResponse<List<RoutineResponse>> getRoutines() {
        return ApiResponse.of(routineService.getRoutines());
    }

    @GetMapping("/{routineId}")
    public ApiResponse<RoutineResponse> getRoutine(@PathVariable Long routineId) {
        return ApiResponse.of(routineService.getRoutine(routineId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RoutineResponse> createRoutine(@Valid @RequestBody CreateRoutineRequest request) {
        return ApiResponse.of(routineService.createRoutine(request));
    }

    @PatchMapping("/{routineId}")
    public ApiResponse<RoutineResponse> updateRoutine(
            @PathVariable Long routineId,
            @Valid @RequestBody UpdateRoutineRequest request
    ) {
        return ApiResponse.of(routineService.updateRoutine(routineId, request));
    }

    @DeleteMapping("/{routineId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRoutine(@PathVariable Long routineId) {
        routineService.deleteRoutine(routineId);
    }
}
