package com.likelion.hackathon_be.competition.application;

import com.likelion.hackathon_be.competition.dto.CompetitionLeaderboardResponse;
import java.time.YearMonth;

public interface CompetitionService {

    CompetitionLeaderboardResponse getLeaderboard(YearMonth month);
}
