package com.likelion.hackathon_be.home.dto;

public record HomeUnlockProgressResponse(
        Integer nextItemMilestonePoints,
        Integer nextStoryEpisodeNumber,
        Integer nextStoryRequiredStreakDays
) {
}
