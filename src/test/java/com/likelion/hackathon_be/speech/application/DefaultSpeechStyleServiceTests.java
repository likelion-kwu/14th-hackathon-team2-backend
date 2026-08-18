package com.likelion.hackathon_be.speech.application;

import java.util.List;
import java.util.Optional;

import com.likelion.hackathon_be.ai.AiMutationLockManager;
import com.likelion.hackathon_be.ai.openai.OpenAiGateway;
import com.likelion.hackathon_be.ai.openai.OpenAiImageInput;
import com.likelion.hackathon_be.avatar.domain.Avatar;
import com.likelion.hackathon_be.avatar.repository.AvatarRepository;
import com.likelion.hackathon_be.common.auth.CurrentUser;
import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.speech.dto.ApplySpeechPresetRequest;
import com.likelion.hackathon_be.speech.dto.ApplySpeechPresetResponse;
import com.likelion.hackathon_be.speech.repository.SpeechStyleExampleRepository;
import com.likelion.hackathon_be.speech.repository.SpeechStyleProfileRepository;
import com.likelion.hackathon_be.user.domain.User;
import com.likelion.hackathon_be.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultSpeechStyleServiceTests {
    private static final Long USER_ID = 41L;

    private SpeechStyleProfileRepository profileRepository;
    private SpeechProfileActivator profileActivator;
    private SpeechAnalysisJobInvalidator jobInvalidator;
    private DefaultSpeechStyleService service;

    @BeforeEach
    void setUp() throws Exception {
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        AvatarRepository avatarRepository = mock(AvatarRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        profileRepository = mock(SpeechStyleProfileRepository.class);
        profileActivator = mock(SpeechProfileActivator.class);
        jobInvalidator = mock(SpeechAnalysisJobInvalidator.class);

        when(currentUserProvider.getCurrentUser()).thenReturn(new CurrentUser(USER_ID));
        when(avatarRepository.findByUserId(USER_ID)).thenReturn(Optional.of(mock(Avatar.class)));
        User user = mock(User.class);
        when(user.getNickname()).thenReturn("테스터");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        ObjectMapper objectMapper = new ObjectMapper();
        SafeDialogueCatalog safeDialogueCatalog = new SafeDialogueCatalog(objectMapper);
        safeDialogueCatalog.load();
        DialogueCandidateGenerator dialogueGenerator = new DialogueCandidateGenerator(
                new UnavailableGateway(),
                objectMapper,
                safeDialogueCatalog
        );
        service = new DefaultSpeechStyleService(
                currentUserProvider,
                avatarRepository,
                userRepository,
                profileRepository,
                mock(SpeechStyleExampleRepository.class),
                dialogueGenerator,
                profileActivator,
                new AiMutationLockManager(),
                mock(TimeProvider.class),
                mock(TransactionTemplate.class),
                objectMapper,
                jobInvalidator
        );
    }

    @Test
    void firstCalmPresetUsesSafeFallbackWhenAiIsUnavailable() {
        when(profileRepository.existsByUserId(USER_ID)).thenReturn(false);

        ApplySpeechPresetResponse response = service.applyPreset(new ApplySpeechPresetRequest("CALM"));

        assertThat(response.sourceType()).isEqualTo("PRESET");
        assertThat(response.presetCode()).isEqualTo("CALM");
        assertThat(response.dialogueCount()).isEqualTo(40);
        verify(profileActivator).activate(eq(USER_ID), any(SpeechProfileCandidate.class), any());
        verify(jobInvalidator).invalidateUnfinished(USER_ID);
    }

    @Test
    void existingProfileIsPreservedWhenStrictCalmGenerationFails() {
        when(profileRepository.existsByUserId(USER_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.applyPreset(new ApplySpeechPresetRequest("CALM")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DIALOGUE_GENERATION_FAILED));
        verify(profileActivator, never()).activate(eq(USER_ID), any(), any());
        verify(jobInvalidator, never()).invalidateUnfinished(USER_ID);
    }

    private static final class UnavailableGateway implements OpenAiGateway {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public JsonNode structuredResponse(
                String schemaName,
                String promptVersion,
                String instructions,
                String inputText,
                List<OpenAiImageInput> images,
                JsonNode schema,
                int maxOutputTokens
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public byte[] editImage(
                String promptVersion,
                String prompt,
                List<OpenAiImageInput> images,
                OpenAiImageInput mask,
                String size,
                String quality
        ) {
            throw new UnsupportedOperationException();
        }
    }
}
