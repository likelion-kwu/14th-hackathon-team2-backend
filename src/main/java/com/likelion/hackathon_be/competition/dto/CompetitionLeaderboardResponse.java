package com.likelion.hackathon_be.competition.dto;

import java.util.List;

public record CompetitionLeaderboardResponse(
        String month,
        List<CompetitionRankingEntryResponse> ranking,
        Integer myRank,
        int myEarnedPoints
) {
}
