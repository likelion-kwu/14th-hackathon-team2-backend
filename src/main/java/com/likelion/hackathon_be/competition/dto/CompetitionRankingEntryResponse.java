package com.likelion.hackathon_be.competition.dto;

public record CompetitionRankingEntryResponse(
        int rank,
        String nickname,
        int earnedPoints,
        boolean me
) {
}
