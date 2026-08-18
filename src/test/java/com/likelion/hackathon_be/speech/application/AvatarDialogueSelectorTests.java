package com.likelion.hackathon_be.speech.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import com.likelion.hackathon_be.avatar.dto.AvatarDialogueSelectionResponse;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.speech.domain.AvatarDialogue;
import com.likelion.hackathon_be.speech.domain.DialogueSituation;
import com.likelion.hackathon_be.speech.domain.SpeechStyleProfile;
import com.likelion.hackathon_be.speech.repository.AvatarDialogueRepository;
import com.likelion.hackathon_be.speech.repository.SpeechStyleProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvatarDialogueSelectorTests {
    private static final Long USER_ID = 41L;
    private static final Long PROFILE_ID = 73L;
    private static final DialogueSituation SITUATION = DialogueSituation.ROUTINE_AVAILABLE;
    private static final Instant NOW = Instant.parse("2026-08-19T06:00:00Z");

    private SpeechStyleProfileRepository profileRepository;
    private AvatarDialogueRepository dialogueRepository;
    private AvatarDialogueSelector selector;

    @BeforeEach
    void setUp() {
        profileRepository = mock(SpeechStyleProfileRepository.class);
        dialogueRepository = mock(AvatarDialogueRepository.class);
        TimeProvider timeProvider = mock(TimeProvider.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

        when(timeProvider.now()).thenReturn(NOW);
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        selector = new AvatarDialogueSelector(
                profileRepository,
                dialogueRepository,
                timeProvider,
                transactionTemplate
        );
    }

    @Test
    void avoidsDialogueUsedWithinLastTwentyFourHours() {
        configureProfile();
        AvatarDialogue recentlyUsed = dialogue("최근 사용", NOW.minus(1, ChronoUnit.HOURS));
        AvatarDialogue notRecentlyUsed = dialogue("24시간 경계 사용", NOW.minus(24, ChronoUnit.HOURS));
        when(dialogueRepository.findAllByProfileIdAndSituation(PROFILE_ID, SITUATION))
                .thenReturn(List.of(recentlyUsed, notRecentlyUsed));

        AvatarDialogueSelectionResponse response = selector.selectForUser(USER_ID, SITUATION);

        assertThat(response.content()).isEqualTo("24시간 경계 사용");
        assertThat(notRecentlyUsed.getLastUsedAt()).isEqualTo(NOW);
        assertThat(recentlyUsed.getLastUsedAt()).isEqualTo(NOW.minus(1, ChronoUnit.HOURS));
    }

    @Test
    void selectsLeastRecentlyUsedWhenEveryCandidateWasUsedRecently() {
        configureProfile();
        AvatarDialogue oldestUse = dialogue("가장 오래된 사용", NOW.minus(23, ChronoUnit.HOURS));
        AvatarDialogue newerUse = dialogue("더 최근 사용", NOW.minus(2, ChronoUnit.HOURS));
        when(dialogueRepository.findAllByProfileIdAndSituation(PROFILE_ID, SITUATION))
                .thenReturn(List.of(newerUse, oldestUse));

        AvatarDialogueSelectionResponse response = selector.selectForUser(USER_ID, SITUATION);

        assertThat(response.content()).isEqualTo("가장 오래된 사용");
        assertThat(oldestUse.getLastUsedAt()).isEqualTo(NOW);
        assertThat(newerUse.getLastUsedAt()).isEqualTo(NOW.minus(2, ChronoUnit.HOURS));
    }

    @Test
    void rejectsSelectionWithConflictWhenSpeechProfileIsNotConfigured() {
        when(profileRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> selector.selectForUser(USER_ID, SITUATION))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.SPEECH_STYLE_NOT_CONFIGURED);
                    assertThat(exception.getErrorCode().status()).isEqualTo(409);
                });
        verify(dialogueRepository, never()).findAllByProfileIdAndSituation(any(), any());
    }

    private void configureProfile() {
        SpeechStyleProfile profile = mock(SpeechStyleProfile.class);
        when(profile.getId()).thenReturn(PROFILE_ID);
        when(profileRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(profile));
    }

    private AvatarDialogue dialogue(String content, Instant lastUsedAt) {
        AvatarDialogue dialogue = AvatarDialogue.create(
                PROFILE_ID,
                SITUATION,
                content,
                false,
                false,
                NOW.minus(30, ChronoUnit.DAYS)
        );
        dialogue.recordUse(lastUsedAt);
        return dialogue;
    }
}
