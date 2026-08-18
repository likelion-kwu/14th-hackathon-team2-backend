package com.likelion.hackathon_be.competition.application;

import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.competition.dto.CompetitionLeaderboardResponse;
import java.time.YearMonth;
import org.springframework.stereotype.Service;

@Service
public class NotImplementedCompetitionService implements CompetitionService {

    @Override
    public CompetitionLeaderboardResponse getLeaderboard(YearMonth month) {
        throw new FeatureNotImplementedException("Competition");
    }
}
