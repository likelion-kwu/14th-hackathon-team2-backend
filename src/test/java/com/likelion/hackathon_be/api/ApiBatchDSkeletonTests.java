package com.likelion.hackathon_be.api;

import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.competition.application.NotImplementedCompetitionService;
import com.likelion.hackathon_be.competition.dto.CompetitionLeaderboardResponse;
import com.likelion.hackathon_be.item.application.NotImplementedItemService;
import com.likelion.hackathon_be.item.dto.ItemResponse;
import com.likelion.hackathon_be.record.application.NotImplementedRecordService;
import com.likelion.hackathon_be.story.application.NotImplementedStoryService;
import com.likelion.hackathon_be.story.dto.StoryEpisodeResponse;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiBatchDSkeletonTests {

    @Test
    void batchDServicesUse501Placeholders() {
        assertThatThrownBy(() -> new NotImplementedRecordService().getRecords(null, null))
                .isInstanceOfSatisfying(FeatureNotImplementedException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_IMPLEMENTED));
        assertThatThrownBy(() -> new NotImplementedStoryService().getStories())
                .isInstanceOf(FeatureNotImplementedException.class);
        assertThatThrownBy(() -> new NotImplementedItemService().getItems(null, false))
                .isInstanceOf(FeatureNotImplementedException.class);
        assertThatThrownBy(() -> new NotImplementedCompetitionService().getLeaderboard(null))
                .isInstanceOf(FeatureNotImplementedException.class);
    }

    @Test
    void itemResponseDoesNotExposePriceOrLayoutFields() {
        assertThat(Arrays.stream(ItemResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("id", "name", "type", "assetKey", "owned", "equipped", "acquiredAt");
    }

    @Test
    void storyEpisodeResponseDoesNotExposeFrontendContentFields() {
        assertThat(Arrays.stream(StoryEpisodeResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("episodeNumber", "requiredStreakDays", "unlocked", "unlockedAt");
    }

    @Test
    void competitionResponseMatchesLeaderboardContract() {
        assertThat(Arrays.stream(CompetitionLeaderboardResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .containsExactly("month", "ranking", "myRank", "myEarnedPoints");
    }
}
