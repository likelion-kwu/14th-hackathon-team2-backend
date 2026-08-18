package com.likelion.hackathon_be.routine.api;

import com.likelion.hackathon_be.common.api.ApiResponse;
import com.likelion.hackathon_be.routine.application.DailyRoutineService;
import com.likelion.hackathon_be.routine.dto.DailyRoutineListResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/daily-routines")
public class DailyRoutineController {

    private final DailyRoutineService dailyRoutineService;

    public DailyRoutineController(DailyRoutineService dailyRoutineService) {
        this.dailyRoutineService = dailyRoutineService;
    }

    @GetMapping
    public ApiResponse<DailyRoutineListResponse> getDailyRoutines(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.of(dailyRoutineService.getDailyRoutines(date));
    }
}
