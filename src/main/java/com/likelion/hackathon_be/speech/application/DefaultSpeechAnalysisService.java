package com.likelion.hackathon_be.speech.application;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.likelion.hackathon_be.ai.AiMutationLockManager;
import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.speech.domain.SpeechAnalysisJob;
import com.likelion.hackathon_be.speech.domain.SpeechAnalysisJobStatus;
import com.likelion.hackathon_be.speech.domain.SpeechStyleProfile;
import com.likelion.hackathon_be.speech.domain.SpeechSourceType;
import com.likelion.hackathon_be.speech.dto.CreateSpeechAnalysisJobResponse;
import com.likelion.hackathon_be.speech.dto.SpeechAnalysisJobResponse;
import com.likelion.hackathon_be.speech.dto.SpeechAnalysisJobResultResponse;
import com.likelion.hackathon_be.speech.dto.SpeechParticipantResponse;
import com.likelion.hackathon_be.speech.dto.StartSpeechAnalysisRequest;
import com.likelion.hackathon_be.speech.dto.StartSpeechAnalysisResponse;
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
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
public class DefaultSpeechAnalysisService implements SpeechAnalysisService {
    private static final int POLL_AFTER_MS = 2_000;
    private static final Duration JOB_LIFETIME = Duration.ofMinutes(10);
    private static final Set<SpeechAnalysisJobStatus> NON_TERMINAL = EnumSet.of(
            SpeechAnalysisJobStatus.UPLOADED,
            SpeechAnalysisJobStatus.WAITING_PARTICIPANT_SELECTION,
            SpeechAnalysisJobStatus.PREPROCESSING,
            SpeechAnalysisJobStatus.ANALYZING,
            SpeechAnalysisJobStatus.GENERATING_DIALOGUES
    );

    private final CurrentUserProvider currentUserProvider;
    private final SpeechAnalysisJobRepository jobRepository;
    private final SpeechStyleProfileRepository profileRepository;
    private final AvatarDialogueRepository dialogueRepository;
    private final UserRepository userRepository;
    private final KakaoArchiveReader archiveReader;
    private final KakaoChatParser chatParser;
    private final KakaoTemporaryStore temporaryStore;
    private final KakaoMessagePreprocessor preprocessor;
    private final OpenAiSpeechStyleAnalyzer styleAnalyzer;
    private final DialogueCandidateGenerator dialogueGenerator;
    private final SpeechProfileActivator profileActivator;
    private final AiMutationLockManager lockManager;
    private final TimeProvider timeProvider;
    private final TransactionTemplate transactionTemplate;
    private final ThreadPoolExecutor worker = new ThreadPoolExecutor(
            1,
            2,
            60,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(10),
            runnable -> {
                Thread thread = new Thread(runnable, "speech-analysis-worker");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    public DefaultSpeechAnalysisService(
            CurrentUserProvider currentUserProvider,
            SpeechAnalysisJobRepository jobRepository,
            SpeechStyleProfileRepository profileRepository,
            AvatarDialogueRepository dialogueRepository,
            UserRepository userRepository,
            KakaoArchiveReader archiveReader,
            KakaoChatParser chatParser,
            KakaoTemporaryStore temporaryStore,
            KakaoMessagePreprocessor preprocessor,
            OpenAiSpeechStyleAnalyzer styleAnalyzer,
            DialogueCandidateGenerator dialogueGenerator,
            SpeechProfileActivator profileActivator,
            AiMutationLockManager lockManager,
            TimeProvider timeProvider,
            TransactionTemplate transactionTemplate
    ) {
        this.currentUserProvider = currentUserProvider;
        this.jobRepository = jobRepository;
        this.profileRepository = profileRepository;
        this.dialogueRepository = dialogueRepository;
        this.userRepository = userRepository;
        this.archiveReader = archiveReader;
        this.chatParser = chatParser;
        this.temporaryStore = temporaryStore;
        this.preprocessor = preprocessor;
        this.styleAnalyzer = styleAnalyzer;
        this.dialogueGenerator = dialogueGenerator;
        this.profileActivator = profileActivator;
        this.lockManager = lockManager;
        this.timeProvider = timeProvider;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public CreateSpeechAnalysisJobResponse createJob(MultipartFile file) {
        Long userId = currentUserProvider.getCurrentUser().id();
        String chatText = archiveReader.readSingleText(file);
        KakaoChatData chatData = chatParser.parse(chatText);
        return lockManager.withUserLock(userId, () -> createParsedJob(userId, chatData));
    }

    private CreateSpeechAnalysisJobResponse createParsedJob(Long userId, KakaoChatData chatData) {
        UUID jobId = UUID.randomUUID();
        Instant now = timeProvider.now();
        Instant expiresAt = now.plus(JOB_LIFETIME);
        temporaryStore.save(jobId, chatData);
        try {
            List<UUID> invalidated = transactionTemplate.execute(status -> {
                List<SpeechAnalysisJob> previous = jobRepository.findAllByUserIdAndStatusIn(userId, NON_TERMINAL);
                previous.forEach(job -> job.transitionTo(SpeechAnalysisJobStatus.EXPIRED, now));
                jobRepository.save(SpeechAnalysisJob.create(jobId, userId, expiresAt, now));
                return previous.stream().map(SpeechAnalysisJob::getId).toList();
            });
            if (invalidated != null) {
                invalidated.forEach(temporaryStore::delete);
            }
        } catch (RuntimeException exception) {
            temporaryStore.delete(jobId);
            throw exception;
        }
        return new CreateSpeechAnalysisJobResponse(
                jobId,
                SpeechAnalysisJobStatus.WAITING_PARTICIPANT_SELECTION.name(),
                chatData.participants().stream()
                        .map(participant -> new SpeechParticipantResponse(participant.id(), participant.displayName()))
                        .toList(),
                offset(expiresAt)
        );
    }

    @Override
    public StartSpeechAnalysisResponse startAnalysis(UUID jobId, StartSpeechAnalysisRequest request) {
        Long userId = currentUserProvider.getCurrentUser().id();
        return lockManager.withUserLock(userId, () -> startAnalysisLocked(jobId, userId, request));
    }

    private StartSpeechAnalysisResponse startAnalysisLocked(
            UUID jobId,
            Long userId,
            StartSpeechAnalysisRequest request
    ) {
        SpeechAnalysisJob job = findOwnedJob(jobId, userId);
        expireIfNeeded(job);
        if (job.getStatus() != SpeechAnalysisJobStatus.WAITING_PARTICIPANT_SELECTION) {
            throw new BusinessException(ErrorCode.AI_ANALYSIS_FAILED);
        }

        KakaoChatData chatData;
        try {
            chatData = temporaryStore.read(jobId);
        } catch (RuntimeException exception) {
            failJob(jobId, userId);
            throw new BusinessException(ErrorCode.AI_ANALYSIS_FAILED);
        }
        PreprocessedSpeechData data = preprocessor.preprocess(chatData, request.participantId());
        if (data == null) {
            throw new BusinessException(ErrorCode.PARTICIPANT_NOT_FOUND);
        }
        if (data.validMessageCount() < 50) {
            failJob(jobId, userId);
            temporaryStore.delete(jobId);
            throw new BusinessException(ErrorCode.INSUFFICIENT_MESSAGES);
        }

        if (!transition(jobId, userId, SpeechAnalysisJobStatus.WAITING_PARTICIPANT_SELECTION,
                SpeechAnalysisJobStatus.PREPROCESSING)) {
            throw new BusinessException(ErrorCode.AI_ANALYSIS_FAILED);
        }
        try {
            worker.execute(() -> runAnalysis(jobId, userId, data));
        } catch (RejectedExecutionException exception) {
            failJob(jobId, userId);
            temporaryStore.delete(jobId);
            throw new BusinessException(ErrorCode.AI_ANALYSIS_FAILED);
        }
        temporaryStore.delete(jobId);
        return new StartSpeechAnalysisResponse(jobId, SpeechAnalysisJobStatus.PREPROCESSING.name(), POLL_AFTER_MS);
    }

    @Override
    public SpeechAnalysisJobResponse getJob(UUID jobId) {
        Long userId = currentUserProvider.getCurrentUser().id();
        SpeechAnalysisJob job = findOwnedJob(jobId, userId);
        expireIfNeeded(job);
        SpeechAnalysisJobStatus status = job.getStatus();
        if (status == SpeechAnalysisJobStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.ANALYSIS_EXPIRED);
        }
        SpeechAnalysisJobResultResponse result = status == SpeechAnalysisJobStatus.COMPLETED
                ? completedResult(userId)
                : null;
        Integer pollAfter = NON_TERMINAL.contains(status) ? POLL_AFTER_MS : null;
        return new SpeechAnalysisJobResponse(job.getId(), status.name(), pollAfter, offset(job.getExpiresAt()), result);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void expireStaleJobs() {
        Instant now = timeProvider.now();
        List<UUID> expired = transactionTemplate.execute(status -> {
            List<SpeechAnalysisJob> jobs = jobRepository.findAllByStatusInAndExpiresAtLessThanEqual(NON_TERMINAL, now);
            jobs.forEach(job -> job.transitionTo(SpeechAnalysisJobStatus.EXPIRED, now));
            return jobs.stream().map(SpeechAnalysisJob::getId).toList();
        });
        if (expired != null) {
            expired.forEach(temporaryStore::delete);
        }
        Set<UUID> active = jobRepository.findAllByStatusIn(NON_TERMINAL).stream()
                .map(SpeechAnalysisJob::getId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        temporaryStore.cleanupExcept(active);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverStaleJobsOnStartup() {
        expireStaleJobs();
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }

    private void runAnalysis(UUID jobId, Long userId, PreprocessedSpeechData data) {
        long started = System.nanoTime();
        try {
            if (!transition(jobId, userId, SpeechAnalysisJobStatus.PREPROCESSING, SpeechAnalysisJobStatus.ANALYZING)) {
                return;
            }
            AnalyzedSpeechProfile analyzed = styleAnalyzer.analyze(data);
            if (!transition(jobId, userId, SpeechAnalysisJobStatus.ANALYZING,
                    SpeechAnalysisJobStatus.GENERATING_DIALOGUES)) {
                return;
            }
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
            List<DialogueCandidate> dialogues = dialogueGenerator.generate(
                    analyzed.profile(),
                    user.getNickname(),
                    analyzed.allowedProfanity()
            );
            lockManager.withUserLock(userId, () -> {
                completeIfCurrent(jobId, userId, analyzed.profile(), dialogues);
                return null;
            });
            log.info(
                    "speech_analysis jobId={} userId={} status=completed validMessages={} elapsedMs={}",
                    jobId,
                    userId,
                    data.validMessageCount(),
                    (System.nanoTime() - started) / 1_000_000
            );
        } catch (RuntimeException exception) {
            failJob(jobId, userId);
            log.warn(
                    "speech_analysis jobId={} userId={} status=failed errorCode={} elapsedMs={}",
                    jobId,
                    userId,
                    errorCode(exception),
                    (System.nanoTime() - started) / 1_000_000
            );
        } finally {
            temporaryStore.delete(jobId);
        }
    }

    private boolean completeIfCurrent(
            UUID jobId,
            Long userId,
            SpeechProfileCandidate candidate,
            List<DialogueCandidate> dialogues
    ) {
        Boolean completed = transactionTemplate.execute(status -> {
            SpeechAnalysisJob job = jobRepository.findOwnedForUpdate(jobId, userId).orElse(null);
            if (job == null || job.getStatus() != SpeechAnalysisJobStatus.GENERATING_DIALOGUES
                    || job.isExpiredAt(timeProvider.now()) || !isLatest(jobId, userId)) {
                return false;
            }
            SpeechStyleProfile current = profileRepository.findByUserId(userId).orElse(null);
            if (current != null && current.getUpdatedAt().isAfter(job.getCreatedAt())) {
                job.transitionTo(SpeechAnalysisJobStatus.EXPIRED, timeProvider.now());
                return false;
            }
            profileActivator.activate(userId, candidate, dialogues);
            job.transitionTo(SpeechAnalysisJobStatus.COMPLETED, timeProvider.now());
            return true;
        });
        return Boolean.TRUE.equals(completed);
    }

    private boolean transition(
            UUID jobId,
            Long userId,
            SpeechAnalysisJobStatus expected,
            SpeechAnalysisJobStatus next
    ) {
        Boolean changed = transactionTemplate.execute(status -> {
            SpeechAnalysisJob job = jobRepository.findOwnedForUpdate(jobId, userId).orElse(null);
            if (job == null || job.getStatus() != expected || job.isExpiredAt(timeProvider.now())
                    || !isLatest(jobId, userId)) {
                return false;
            }
            job.transitionTo(next, timeProvider.now());
            return true;
        });
        return Boolean.TRUE.equals(changed);
    }

    private void failJob(UUID jobId, Long userId) {
        transactionTemplate.executeWithoutResult(status -> jobRepository.findOwnedForUpdate(jobId, userId)
                .filter(job -> NON_TERMINAL.contains(job.getStatus()))
                .ifPresent(job -> job.transitionTo(SpeechAnalysisJobStatus.FAILED, timeProvider.now())));
    }

    private void expireIfNeeded(SpeechAnalysisJob job) {
        if (job.getStatus() == SpeechAnalysisJobStatus.EXPIRED || job.isExpiredAt(timeProvider.now())) {
            transactionTemplate.executeWithoutResult(status -> jobRepository.findOwnedForUpdate(job.getId(), job.getUserId())
                    .ifPresent(locked -> locked.transitionTo(SpeechAnalysisJobStatus.EXPIRED, timeProvider.now())));
            temporaryStore.delete(job.getId());
            throw new BusinessException(ErrorCode.ANALYSIS_EXPIRED);
        }
    }

    private SpeechAnalysisJob findOwnedJob(UUID jobId, Long userId) {
        return jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_JOB_NOT_FOUND));
    }

    private boolean isLatest(UUID jobId, Long userId) {
        return jobRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(latest -> latest.getId().equals(jobId))
                .orElse(false);
    }

    private SpeechAnalysisJobResultResponse completedResult(Long userId) {
        SpeechStyleProfile profile = profileRepository.findByUserId(userId).orElse(null);
        if (profile == null || profile.getSourceType() != SpeechSourceType.KAKAO_CHAT) {
            return null;
        }
        return new SpeechAnalysisJobResultResponse(
                profile.getSourceType().name(),
                Math.toIntExact(dialogueRepository.countByProfileId(profile.getId())),
                profile.getValidMessageCount()
        );
    }

    private OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, timeProvider.serviceZone());
    }

    private String errorCode(RuntimeException exception) {
        return exception instanceof BusinessException businessException
                ? businessException.getErrorCode().name()
                : exception.getClass().getSimpleName();
    }
}
