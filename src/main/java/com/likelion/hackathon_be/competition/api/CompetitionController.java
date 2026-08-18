package com.likelion.hackathon_be.competition.api;

import com.likelion.hackathon_be.common.api.ApiResponse;
import com.likelion.hackathon_be.competition.application.CompetitionService;
import com.likelion.hackathon_be.competition.dto.CompetitionLeaderboardResponse;
import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/competition")
public class CompetitionController {

    private final CompetitionService competitionService;

    public CompetitionController(CompetitionService competitionService) {
        this.competitionService = competitionService;
    }

    @GetMapping("/leaderboard")
    public ApiResponse<CompetitionLeaderboardResponse> getLeaderboard(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month
    ) {
        return ApiResponse.of(competitionService.getLeaderboard(month));
    }
}
