package com.likelion.hackathon_be.speech;

import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.speech.application.NotImplementedSpeechAnalysisService;
import com.likelion.hackathon_be.speech.application.NotImplementedSpeechStyleService;
import com.likelion.hackathon_be.speech.domain.DialogueSituation;
import com.likelion.hackathon_be.speech.domain.SentenceLength;
import com.likelion.hackathon_be.speech.domain.SpeechAnalysisJobStatus;
import com.likelion.hackathon_be.speech.domain.SpeechAttributeLevel;
import com.likelion.hackathon_be.speech.domain.SpeechLevel;
import com.likelion.hackathon_be.speech.dto.UpdateSpeechStyleRequest;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpeechApiSkeletonTests {

    @Test
    void speechStyleSkeletonUses501Placeholder() {
        NotImplementedSpeechStyleService service = new NotImplementedSpeechStyleService();

        assertThatThrownBy(service::getPresets)
                .isInstanceOfSatisfying(FeatureNotImplementedException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_IMPLEMENTED));
    }

    @Test
    void speechAnalysisSkeletonUses501Placeholder() {
        NotImplementedSpeechAnalysisService service = new NotImplementedSpeechAnalysisService();

        assertThatThrownBy(() -> service.getJob(UUID.randomUUID()))
                .isInstanceOfSatisfying(FeatureNotImplementedException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_IMPLEMENTED));
    }

    @Test
    void updateSpeechStyleRequestOnlyContainsPatchSupportedSettings() {
        assertThat(Arrays.stream(UpdateSpeechStyleRequest.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly(
                        "speechLevel",
                        "sentenceLength",
                        "directness",
                        "warmth",
                        "playfulness",
                        "profanityEnabled"
                );
    }

    @Test
    void speechApiEnumsMatchSpec() {
        assertThat(SpeechLevel.values()).containsExactly(SpeechLevel.BANMAL, SpeechLevel.JONDAEMAL);
        assertThat(SentenceLength.values()).containsExactly(SentenceLength.SHORT, SentenceLength.MEDIUM, SentenceLength.LONG);
        assertThat(SpeechAttributeLevel.values()).containsExactly(
                SpeechAttributeLevel.LOW,
                SpeechAttributeLevel.MEDIUM,
                SpeechAttributeLevel.HIGH
        );
        assertThat(DialogueSituation.values()).containsExactly(
                DialogueSituation.ROUTINE_UPCOMING,
                DialogueSituation.ROUTINE_AVAILABLE,
                DialogueSituation.ROUTINE_REMINDER,
                DialogueSituation.ROUTINE_COMPLETED,
                DialogueSituation.ALL_COMPLETED,
                DialogueSituation.STREAK_CONTINUED,
                DialogueSituation.STREAK_BROKEN,
                DialogueSituation.RETURN_AFTER_ABSENCE
        );
        assertThat(SpeechAnalysisJobStatus.values()).containsExactly(
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
}
