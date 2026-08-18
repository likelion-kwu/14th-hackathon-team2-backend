package com.likelion.hackathon_be;

import com.likelion.hackathon_be.avatar.domain.AvatarAssetSource;
import com.likelion.hackathon_be.avatar.domain.AvatarGrowthTrack;
import com.likelion.hackathon_be.common.api.ApiResponse;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.common.time.SystemTimeProvider;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.item.domain.Item;
import com.likelion.hackathon_be.item.domain.ItemUnlockRecord;
import com.likelion.hackathon_be.routine.domain.DayOfWeek;
import com.likelion.hackathon_be.routine.domain.RepeatType;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.verification.domain.VerificationType;
import com.likelion.hackathon_be.speech.domain.DialogueSituation;
import com.likelion.hackathon_be.speech.domain.SentenceLength;
import com.likelion.hackathon_be.speech.domain.SpeechAnalysisJob;
import com.likelion.hackathon_be.speech.domain.SpeechAnalysisJobStatus;
import com.likelion.hackathon_be.speech.domain.SpeechAttributeLevel;
import com.likelion.hackathon_be.speech.domain.SpeechExampleCategory;
import com.likelion.hackathon_be.speech.domain.SpeechExampleSourceType;
import com.likelion.hackathon_be.speech.domain.SpeechLevel;
import com.likelion.hackathon_be.speech.domain.SpeechSourceType;
import com.likelion.hackathon_be.speech.domain.SpeechStyleProfile;
import java.lang.reflect.Field;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HackathonBeApplicationTests {

    @Test
    void apiResponseWrapsDataWithoutDatabaseContext() {
        ApiResponse<String> response = ApiResponse.of("ok");

        assertThat(response.data()).isEqualTo("ok");
        assertThat(response.meta()).isNull();
    }

    @Test
    void featureNotImplementedMapsTo501ErrorCode() {
        FeatureNotImplementedException exception = new FeatureNotImplementedException("Session");

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_IMPLEMENTED);
        assertThat(exception.getErrorCode().status()).isEqualTo(501);
    }

    @Test
    void timeProviderUsesAsiaSeoulServiceZone() {
        TimeProvider timeProvider = new SystemTimeProvider();

        assertThat(timeProvider.serviceZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
        assertThat(timeProvider.todayServiceDate()).isNotNull();
    }

    @Test
    void avatarEnumsMatchFrozenDbContract() {
        assertThat(AvatarGrowthTrack.values())
                .containsExactly(
                        AvatarGrowthTrack.SKIN,
                        AvatarGrowthTrack.WELL_BEING,
                        AvatarGrowthTrack.HEALTH_FIT,
                        AvatarGrowthTrack.DIET
                );
        assertThat(AvatarAssetSource.values())
                .containsExactly(AvatarAssetSource.GENERATED, AvatarAssetSource.DEFAULT);
    }

    @Test
    void routineEnumsMatchFrozenDbContract() {
        assertThat(RoutineCategory.values())
                .containsExactly(
                        RoutineCategory.SKIN,
                        RoutineCategory.WELL_BEING,
                        RoutineCategory.HEALTH_FIT,
                        RoutineCategory.DIET,
                        RoutineCategory.TO_DO
                );
        assertThat(RepeatType.values())
                .containsExactly(RepeatType.DAILY, RepeatType.DAYS_OF_WEEK, RepeatType.ONCE);
        assertThat(DayOfWeek.values())
                .containsExactly(
                        DayOfWeek.MON,
                        DayOfWeek.TUE,
                        DayOfWeek.WED,
                        DayOfWeek.THU,
                        DayOfWeek.FRI,
                        DayOfWeek.SAT,
                        DayOfWeek.SUN
                );
        assertThat(VerificationType.values())
                .containsExactly(VerificationType.PHOTO, VerificationType.CHECK);
    }

    @Test
    void itemStoryMappingsDoNotFreezeUndecidedItemTypes() throws NoSuchFieldException {
        Field itemType = Item.class.getDeclaredField("itemType");
        Field requiredPoints = ItemUnlockRecord.class.getDeclaredField("requiredPoints");

        assertThat(itemType.getType()).isEqualTo(String.class);
        assertThat(requiredPoints.getType()).isEqualTo(int.class);
    }

    @Test
    void speechEnumsMatchFrozenDbContract() {
        assertThat(SpeechSourceType.values())
                .containsExactly(SpeechSourceType.KAKAO_CHAT, SpeechSourceType.PRESET);
        assertThat(SpeechLevel.values())
                .containsExactly(SpeechLevel.BANMAL, SpeechLevel.JONDAEMAL);
        assertThat(SentenceLength.values())
                .containsExactly(SentenceLength.SHORT, SentenceLength.MEDIUM, SentenceLength.LONG);
        assertThat(SpeechAttributeLevel.values())
                .containsExactly(SpeechAttributeLevel.LOW, SpeechAttributeLevel.MEDIUM, SpeechAttributeLevel.HIGH);
        assertThat(SpeechExampleCategory.values())
                .containsExactly(
                        SpeechExampleCategory.QUESTION,
                        SpeechExampleCategory.AGREEMENT,
                        SpeechExampleCategory.DISAGREEMENT,
                        SpeechExampleCategory.ENCOURAGEMENT,
                        SpeechExampleCategory.REACTION,
                        SpeechExampleCategory.GENERAL
                );
        assertThat(SpeechExampleSourceType.values())
                .containsExactly(SpeechExampleSourceType.USER_MESSAGE, SpeechExampleSourceType.AI_GENERATED);
        assertThat(DialogueSituation.values())
                .containsExactly(
                        DialogueSituation.ROUTINE_UPCOMING,
                        DialogueSituation.ROUTINE_AVAILABLE,
                        DialogueSituation.ROUTINE_REMINDER,
                        DialogueSituation.ROUTINE_COMPLETED,
                        DialogueSituation.ALL_COMPLETED,
                        DialogueSituation.STREAK_CONTINUED,
                        DialogueSituation.STREAK_BROKEN,
                        DialogueSituation.RETURN_AFTER_ABSENCE
                );
        assertThat(SpeechAnalysisJobStatus.values())
                .containsExactly(
                        SpeechAnalysisJobStatus.UPLOADED,
                        SpeechAnalysisJobStatus.WAITING_PARTICIPANT_SELECTION,
                        SpeechAnalysisJobStatus.PREPROCESSING,
                        SpeechAnalysisJobStatus.ANALYZING,
                        SpeechAnalysisJobStatus.GENERATING_DIALOGUES,
                        SpeechAnalysisJobStatus.COMPLETED,
                        SpeechAnalysisJobStatus.FAILED,
                        SpeechAnalysisJobStatus.EXPIRED
                );
    }

    @Test
    void speechMappingsUseJsonStringAndUuidJobId() throws NoSuchFieldException {
        Field styleJson = SpeechStyleProfile.class.getDeclaredField("styleJson");
        Field jobId = SpeechAnalysisJob.class.getDeclaredField("id");

        assertThat(styleJson.getType()).isEqualTo(String.class);
        assertThat(jobId.getType()).isEqualTo(UUID.class);
    }
}
