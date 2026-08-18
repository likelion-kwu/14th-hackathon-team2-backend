package com.likelion.hackathon_be.story.application;

import java.time.Instant;

public interface StoryProgressionService {

    StoryProgressionResult progressAfterNewDailySuccess(Long userId, Instant unlockedAt);

    StoryProgressionResult currentProgress(Long userId);
}
