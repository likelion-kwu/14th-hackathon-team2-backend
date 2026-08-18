package com.likelion.hackathon_be.routine.api;

import com.likelion.hackathon_be.common.api.ApiResponse;
import com.likelion.hackathon_be.routine.application.RoutineVerificationService;
import com.likelion.hackathon_be.routine.dto.RoutineVerificationResultResponse;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/daily-routines/{dailyRoutineId}/verifications")
public class RoutineVerificationController {

    private final RoutineVerificationService routineVerificationService;

    public RoutineVerificationController(RoutineVerificationService routineVerificationService) {
        this.routineVerificationService = routineVerificationService;
    }

    @PostMapping(value = "/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RoutineVerificationResultResponse> verifyPhoto(
            @PathVariable Long dailyRoutineId,
            @NotNull @RequestParam MultipartFile photo
    ) {
        return ApiResponse.of(routineVerificationService.verifyPhoto(dailyRoutineId, photo));
    }

    @PostMapping("/check")
    public ApiResponse<RoutineVerificationResultResponse> verifyCheck(@PathVariable Long dailyRoutineId) {
        return ApiResponse.of(routineVerificationService.verifyCheck(dailyRoutineId));
    }
}
