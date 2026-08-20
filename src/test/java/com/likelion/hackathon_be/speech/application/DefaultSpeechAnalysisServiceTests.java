package com.likelion.hackathon_be.speech.application;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.likelion.hackathon_be.ai.AiMutationLockManager;
import com.likelion.hackathon_be.common.auth.CurrentUser;
import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.speech.domain.DialogueSituation;
import com.likelion.hackathon_be.speech.domain.SpeechAnalysisJob;
import com.likelion.hackathon_be.speech.domain.SpeechAnalysisJobStatus;
import com.likelion.hackathon_be.speech.domain.SpeechSourceType;
import com.likelion.hackathon_be.speech.dto.StartSpeechAnalysisRequest;
import com.likelion.hackathon_be.speech.infrastructure.AnalyzedSpeechProfile;
import com.likelion.hackathon_be.speech.infrastructure.KakaoArchiveReader;
import com.likelion.hackathon_be.speech.infrastructure.KakaoChatData;
import com.likelion.hackathon_be.speech.infrastructure.KakaoChatParser;
import com.likelion.hackathon_be.speech.infrastructure.KakaoMessagePreprocessor;
import com.likelion.hackathon_be.speech.infrastructure.KakaoTemporaryStore;
import com.likelion.hackathon_be.speech.infrastructure.OpenAiSpeechStyleAnalyzer;
import com.likelion.hackathon_be.speech.infrastructure.PreprocessedSpeechData;
import com.likelion.hackathon_be.speech.repository.AvatarDialogueRepository;
import com.likelion.hackathon_be.speech.repository.SpeechAnalysisJobRepository;
import com.likelion.hackathon_be.speech.repository.SpeechStyleProfileRepository;
import com.likelion.hackathon_be.user.domain.User;
import com.likelion.hackathon_be.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefaultSpeechAnalysisServiceTests {
    private static final Long USER_ID = 41L;
    private static final UUID JOB_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Instant NOW = Instant.parse("2026-08-19T06:00:00Z");

    private SpeechAnalysisJobRepository jobRepository;
    private SpeechStyleProfileRepository profileRepository;
    private UserRepository userRepository;
    private KakaoTemporaryStore temporaryStore;
    private KakaoMessagePreprocessor preprocessor;
    private OpenAiSpeechStyleAnalyzer styleAnalyzer;
    private DialogueCandidateGenerator dialogueGenerator;
    private SpeechProfileActivator profileActivator;
    private DefaultSpeechAnalysisService service;

    @BeforeEach
    void setUp() {
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        jobRepository = mock(SpeechAnalysisJobRepository.class);
        profileRepository = mock(SpeechStyleProfileRepository.class);
        AvatarDialogueRepository dialogueRepository = mock(AvatarDialogueRepository.class);
        userRepository = mock(UserRepository.class);
        temporaryStore = mock(KakaoTemporaryStore.class);
        preprocessor = mock(KakaoMessagePreprocessor.class);
        styleAnalyzer = mock(OpenAiSpeechStyleAnalyzer.class);
        dialogueGenerator = mock(DialogueCandidateGenerator.class);
        profileActivator = mock(SpeechProfileActivator.class);
        TimeProvider timeProvider = mock(TimeProvider.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);

        when(currentUserProvider.getCurrentUser()).thenReturn(new CurrentUser(USER_ID));
        when(timeProvider.now()).thenReturn(NOW);
        when(timeProvider.serviceZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        service = new DefaultSpeechAnalysisService(
                currentUserProvider,
                jobRepository,
                profileRepository,
                dialogueRepository,
                userRepository,
                mock(KakaoArchiveReader.class),
                mock(KakaoChatParser.class),
                temporaryStore,
                preprocessor,
                styleAnalyzer,
                dialogueGenerator,
                profileActivator,
                new AiMutationLockManager(),
                timeProvider,
                transactionTemplate
        );
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void expiredJobLookupReturnsGoneAndDeletesTemporaryData() {
        SpeechAnalysisJob job = job(
                JOB_ID,
                NOW.minus(1, ChronoUnit.SECONDS),
                NOW.minus(10, ChronoUnit.MINUTES),
                SpeechAnalysisJobStatus.WAITING_PARTICIPANT_SELECTION
        );
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(job));
        when(jobRepository.findOwnedForUpdate(JOB_ID, USER_ID)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.getJob(JOB_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ANALYSIS_EXPIRED);
                    assertThat(exception.getErrorCode().status()).isEqualTo(410);
                });

        assertThat(job.getStatus()).isEqualTo(SpeechAnalysisJobStatus.EXPIRED);
        verify(temporaryStore).delete(JOB_ID);
    }

    @Test
    void fortyNineMessagesFailWithoutAiAndDeleteTemporaryData() {
        SpeechAnalysisJob job = job(
                JOB_ID,
                NOW.plus(10, ChronoUnit.MINUTES),
                NOW,
                SpeechAnalysisJobStatus.WAITING_PARTICIPANT_SELECTION
        );
        KakaoChatData chatData = new KakaoChatData(List.of(), List.of());
        PreprocessedSpeechData data = new PreprocessedSpeechData(List.of(), 49, Map.of(), Map.of());
        when(jobRepository.findByIdAndUserId(JOB_ID, USER_ID)).thenReturn(Optional.of(job));
        when(jobRepository.findOwnedForUpdate(JOB_ID, USER_ID)).thenReturn(Optional.of(job));
        when(temporaryStore.read(JOB_ID)).thenReturn(chatData);
        when(preprocessor.preprocess(chatData, "p1")).thenReturn(data);

        assertThatThrownBy(() -> service.startAnalysis(JOB_ID, new StartSpeechAnalysisRequest("p1")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_MESSAGES));

        assertThat(job.getStatus()).isEqualTo(SpeechAnalysisJobStatus.FAILED);
        verify(temporaryStore).delete(JOB_ID);
        verifyNoInteractions(styleAnalyzer, dialogueGenerator);
    }

    @Test
    void staleJobResultDoesNotReplaceCurrentProfile() throws Exception {
        SpeechAnalysisJob job = job(
                JOB_ID,
                NOW.plus(10, ChronoUnit.MINUTES),
                NOW.minus(5, ChronoUnit.MINUTES),
                SpeechAnalysisJobStatus.PREPROCESSING
        );
        SpeechAnalysisJob newerJob = job(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                NOW.plus(10, ChronoUnit.MINUTES),
                NOW.minus(1, ChronoUnit.MINUTES),
                SpeechAnalysisJobStatus.WAITING_PARTICIPANT_SELECTION
        );
        PreprocessedSpeechData data = new PreprocessedSpeechData(List.of(), 50, Map.of(), Map.of());
        SpeechProfileCandidate candidate = new SpeechProfileCandidate(
                SpeechSourceType.KAKAO_CHAT,
                null,
                SpeechStyleSettings.calm(),
                "{}",
                false,
                50,
                List.of()
        );
        AnalyzedSpeechProfile analyzed = new AnalyzedSpeechProfile(candidate, Set.of());
        List<DialogueCandidate> dialogues = List.of(new DialogueCandidate(
                DialogueSituation.ROUTINE_AVAILABLE,
                "지금 시작해보자",
                false,
                false
        ));
        User user = mock(User.class);
        when(user.getNickname()).thenReturn("테스터");
        when(jobRepository.findOwnedForUpdate(JOB_ID, USER_ID)).thenReturn(Optional.of(job));
        Queue<Optional<SpeechAnalysisJob>> latestJobs = new ArrayDeque<>(List.of(
                Optional.of(job),
                Optional.of(job),
                Optional.of(newerJob)
        ));
        when(jobRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenAnswer(invocation -> latestJobs.remove());
        when(styleAnalyzer.analyze(data)).thenReturn(analyzed);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(dialogueGenerator.generateWithSafeFallback(eq(candidate), eq("테스터"), anySet())).thenReturn(dialogues);

        invokeRunAnalysis(data);

        assertThat(job.getStatus()).isEqualTo(SpeechAnalysisJobStatus.EXPIRED);
        verify(profileActivator).validateCandidate(candidate, dialogues);
        verify(profileActivator, never()).activate(any(), any(), any());
        verify(profileRepository, never()).findByUserId(USER_ID);
        verify(temporaryStore).delete(JOB_ID);
    }

    @Test
    void kakaoAnalysisUsesSafeDialogueFallbackPathAndCompletesJob() throws Exception {
        SpeechAnalysisJob job = job(
                JOB_ID,
                NOW.plus(10, ChronoUnit.MINUTES),
                NOW,
                SpeechAnalysisJobStatus.PREPROCESSING
        );
        PreprocessedSpeechData data = new PreprocessedSpeechData(List.of(), 50, Map.of(), Map.of());
        SpeechProfileCandidate candidate = new SpeechProfileCandidate(
                SpeechSourceType.KAKAO_CHAT,
                null,
                SpeechStyleSettings.calm(),
                "{}",
                false,
                50,
                List.of()
        );
        AnalyzedSpeechProfile analyzed = new AnalyzedSpeechProfile(candidate, Set.of());
        List<DialogueCandidate> fallbackDialogues = List.of(new DialogueCandidate(
                DialogueSituation.ROUTINE_AVAILABLE,
                "fallback dialogue",
                false,
                false
        ));
        User user = mock(User.class);
        when(user.getNickname()).thenReturn("tester");
        when(jobRepository.findOwnedForUpdate(JOB_ID, USER_ID)).thenReturn(Optional.of(job));
        Queue<Optional<SpeechAnalysisJob>> latestJobs = new ArrayDeque<>(List.of(
                Optional.of(job),
                Optional.of(job),
                Optional.of(job)
        ));
        when(jobRepository.findFirstByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenAnswer(invocation -> latestJobs.remove());
        when(styleAnalyzer.analyze(data)).thenReturn(analyzed);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(dialogueGenerator.generateWithSafeFallback(eq(candidate), eq("tester"), anySet()))
                .thenReturn(fallbackDialogues);
        when(profileRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        invokeRunAnalysis(data);

        assertThat(job.getStatus()).isEqualTo(SpeechAnalysisJobStatus.COMPLETED);
        verify(dialogueGenerator).generateWithSafeFallback(eq(candidate), eq("tester"), anySet());
        verify(dialogueGenerator, never()).generateStrict(any(), any(), anySet());
        verify(profileActivator).validateCandidate(candidate, fallbackDialogues);
        verify(profileActivator).activate(USER_ID, candidate, fallbackDialogues);
        verify(temporaryStore).delete(JOB_ID);
    }

    private SpeechAnalysisJob job(
            UUID id,
            Instant expiresAt,
            Instant createdAt,
            SpeechAnalysisJobStatus status
    ) {
        SpeechAnalysisJob job = SpeechAnalysisJob.create(id, USER_ID, expiresAt, createdAt);
        if (status != SpeechAnalysisJobStatus.WAITING_PARTICIPANT_SELECTION) {
            job.transitionTo(status, createdAt);
        }
        return job;
    }

    private void invokeRunAnalysis(PreprocessedSpeechData data) throws Exception {
        Method method = DefaultSpeechAnalysisService.class.getDeclaredMethod(
                "runAnalysis",
                UUID.class,
                Long.class,
                PreprocessedSpeechData.class
        );
        method.setAccessible(true);
        method.invoke(service, JOB_ID, USER_ID, data);
    }
}
