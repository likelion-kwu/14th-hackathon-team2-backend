package com.likelion.hackathon_be.routine.api;

import com.likelion.hackathon_be.common.api.ApiResponse;
import com.likelion.hackathon_be.routine.application.PhotoMissionService;
import com.likelion.hackathon_be.routine.dto.PhotoMissionResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/daily-routines/{dailyRoutineId}/photo-mission")
public class PhotoMissionController {

    private final PhotoMissionService photoMissionService;

    public PhotoMissionController(PhotoMissionService photoMissionService) {
        this.photoMissionService = photoMissionService;
    }

    @PostMapping
    public ApiResponse<PhotoMissionResponse> preparePhotoMission(@PathVariable Long dailyRoutineId) {
        return ApiResponse.of(photoMissionService.preparePhotoMission(dailyRoutineId));
    }
}
