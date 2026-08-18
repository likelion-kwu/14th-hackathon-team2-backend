package com.likelion.hackathon_be.story.application;

import com.likelion.hackathon_be.routine.dto.AvatarStageChangedResponse;
import com.likelion.hackathon_be.routine.dto.StoryUnlockResponse;
import com.likelion.hackathon_be.routine.dto.SuccessSummaryResponse;
import java.util.List;

public record StoryProgressionResult(
        SuccessSummaryResponse successSummary,
        List<StoryUnlockResponse> unlockedStories,
        AvatarStageChangedResponse avatarStageChanged
) {
}
