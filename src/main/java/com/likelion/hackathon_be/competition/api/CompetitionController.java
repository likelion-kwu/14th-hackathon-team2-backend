package com.likelion.hackathon_be.competition.api;

import com.likelion.hackathon_be.common.api.ApiResponse;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.competition.application.CompetitionService;
import com.likelion.hackathon_be.competition.dto.CompetitionLeaderboardResponse;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/competition")
public class CompetitionController {

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final CompetitionService competitionService;

    public CompetitionController(CompetitionService competitionService) {
        this.competitionService = competitionService;
    }

    @GetMapping("/leaderboard")
    public ApiResponse<CompetitionLeaderboardResponse> getLeaderboard(
            @RequestParam(required = false) String month
    ) {
        return ApiResponse.of(competitionService.getLeaderboard(parseMonth(month)));
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return null;
        }
        try {
            return YearMonth.parse(month, MONTH_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "month must use yyyy-MM format.");
        }
    }
}
