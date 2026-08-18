package com.likelion.hackathon_be.speech.application;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import com.likelion.hackathon_be.ai.AiMutationLockManager;
import com.likelion.hackathon_be.avatar.repository.AvatarRepository;
import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.speech.domain.SpeechStyleExample;
import com.likelion.hackathon_be.speech.domain.SpeechStyleProfile;
import com.likelion.hackathon_be.speech.domain.SpeechSourceType;
import com.likelion.hackathon_be.speech.dto.ApplySpeechPresetRequest;
import com.likelion.hackathon_be.speech.dto.ApplySpeechPresetResponse;
import com.likelion.hackathon_be.speech.dto.SpeechPresetResponse;
import com.likelion.hackathon_be.speech.dto.SpeechStyleResponse;
import com.likelion.hackathon_be.speech.dto.UpdateSpeechStyleRequest;
import com.likelion.hackathon_be.speech.dto.UpdateSpeechStyleResponse;
import com.likelion.hackathon_be.speech.repository.SpeechStyleExampleRepository;
import com.likelion.hackathon_be.speech.repository.SpeechStyleProfileRepository;
import com.likelion.hackathon_be.user.domain.User;
import com.likelion.hackathon_be.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class DefaultSpeechStyleService implements SpeechStyleService {
    private static final String CALM = "CALM";
    private static final Set<String> SAFE_SELF_DIRECTED_PROFANITY = Set.of("씨발", "시발", "ㅅㅂ");

    private final CurrentUserProvider currentUserProvider;
    private final AvatarRepository avatarRepository;
    private final UserRepository userRepository;
    private final SpeechStyleProfileRepository profileRepository;
    private final SpeechStyleExampleRepository exampleRepository;
    private final DialogueCandidateGenerator dialogueGenerator;
    private final SpeechProfileActivator profileActivator;
    private final AiMutationLockManager lockManager;
    private final TimeProvider timeProvider;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final SpeechAnalysisJobInvalidator jobInvalidator;

    public DefaultSpeechStyleService(
            CurrentUserProvider currentUserProvider,
            AvatarRepository avatarRepository,
            UserRepository userRepository,
            SpeechStyleProfileRepository profileRepository,
            SpeechStyleExampleRepository exampleRepository,
            DialogueCandidateGenerator dialogueGenerator,
            SpeechProfileActivator profileActivator,
            AiMutationLockManager lockManager,
            TimeProvider timeProvider,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            SpeechAnalysisJobInvalidator jobInvalidator
    ) {
        this.currentUserProvider = currentUserProvider;
        this.avatarRepository = avatarRepository;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.exampleRepository = exampleRepository;
        this.dialogueGenerator = dialogueGenerator;
        this.profileActivator = profileActivator;
        this.lockManager = lockManager;
        this.timeProvider = timeProvider;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.jobInvalidator = jobInvalidator;
    }

    @Override
    public List<SpeechPresetResponse> getPresets() {
        return List.of(new SpeechPresetResponse(CALM, "차분하게", "짧고 차분한 말투"));
    }

    @Override
    public SpeechStyleResponse getCurrentStyle() {
        Long userId = currentUserProvider.getCurrentUser().id();
        SpeechStyleProfile profile = findProfile(userId);
        return new SpeechStyleResponse(
                profile.getSourceType().name(),
                SpeechStyleSettings.from(profile).toResponse(),
                profile.isProfanityDetected(),
                profile.getValidMessageCount(),
                OffsetDateTime.ofInstant(profile.getUpdatedAt(), timeProvider.serviceZone())
        );
    }

    @Override
    public ApplySpeechPresetResponse applyPreset(ApplySpeechPresetRequest request) {
        if (!CALM.equals(request.presetCode())) {
            throw new BusinessException(ErrorCode.PRESET_NOT_FOUND);
        }
        Long userId = currentUserProvider.getCurrentUser().id();
        return lockManager.withUserLock(userId, () -> applyPresetLocked(userId));
    }

    @Override
    public UpdateSpeechStyleResponse updateStyle(UpdateSpeechStyleRequest request) {
        Long userId = currentUserProvider.getCurrentUser().id();
        return lockManager.withUserLock(userId, () -> updateStyleLocked(userId, request));
    }

    @Override
    public void resetStyle() {
        Long userId = currentUserProvider.getCurrentUser().id();
        lockManager.withUserLock(userId, () -> {
            transactionTemplate.executeWithoutResult(status -> {
                SpeechStyleProfile profile = profileRepository.findByUserIdForUpdate(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.STYLE_NOT_FOUND));
                profileRepository.delete(profile);
            });
            jobInvalidator.invalidateUnfinished(userId);
            return null;
        });
    }

    private ApplySpeechPresetResponse applyPresetLocked(Long userId) {
        if (avatarRepository.findByUserId(userId).isEmpty()) {
            throw new BusinessException(ErrorCode.AVATAR_NOT_CONFIGURED);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        SpeechStyleSettings settings = SpeechStyleSettings.calm();
        SpeechProfileCandidate candidate = new SpeechProfileCandidate(
                SpeechSourceType.PRESET,
                CALM,
                settings,
                styleJson(settings, false, Set.of(), null),
                false,
                null,
                List.of()
        );
        List<DialogueCandidate> dialogues = profileRepository.existsByUserId(userId)
                ? dialogueGenerator.generateStrict(candidate, user.getNickname(), Set.of())
                : dialogueGenerator.generateWithSafeFallback(candidate, user.getNickname(), Set.of());
        profileActivator.activate(userId, candidate, dialogues);
        jobInvalidator.invalidateUnfinished(userId);
        return new ApplySpeechPresetResponse(
                SpeechSourceType.PRESET.name(),
                CALM,
                settings.toResponse(),
                dialogues.size()
        );
    }

    private UpdateSpeechStyleResponse updateStyleLocked(Long userId, UpdateSpeechStyleRequest request) {
        SpeechStyleProfile current = findProfile(userId);
        boolean profanityEnabled = request.profanityEnabled() == null
                ? current.isProfanityEnabled()
                : request.profanityEnabled();
        if (profanityEnabled && !current.isProfanityDetected()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        SpeechStyleSettings settings = new SpeechStyleSettings(
                request.speechLevel() == null ? current.getSpeechLevel() : request.speechLevel(),
                request.sentenceLength() == null ? current.getSentenceLength() : request.sentenceLength(),
                request.directness() == null ? current.getDirectness() : request.directness(),
                request.warmth() == null ? current.getWarmth() : request.warmth(),
                request.playfulness() == null ? current.getPlayfulness() : request.playfulness(),
                current.getEmotionalIntensity(),
                profanityEnabled
        );
        List<SpeechExampleCandidate> examples = exampleRepository.findAllByProfileId(current.getId()).stream()
                .map(this::toCandidate)
                .toList();
        Set<String> allowedProfanity = profanityEnabled ? extractAllowedProfanity(current.getStyleJson()) : Set.of();
        SpeechProfileCandidate candidate = new SpeechProfileCandidate(
                current.getSourceType(),
                current.getPresetCode(),
                settings,
                styleJson(settings, current.isProfanityDetected(), allowedProfanity, current.getStyleJson()),
                current.isProfanityDetected(),
                current.getValidMessageCount(),
                examples
        );
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        List<DialogueCandidate> dialogues = dialogueGenerator.generateStrict(
                candidate,
                user.getNickname(),
                allowedProfanity
        );
        profileActivator.activate(userId, candidate, dialogues);
        jobInvalidator.invalidateUnfinished(userId);
        return new UpdateSpeechStyleResponse(settings.toResponse(), dialogues.size());
    }

    private SpeechStyleProfile findProfile(Long userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STYLE_NOT_FOUND));
    }

    private SpeechExampleCandidate toCandidate(SpeechStyleExample example) {
        return new SpeechExampleCandidate(example.getCategory(), example.getSourceType(), example.getContent());
    }

    private String styleJson(
            SpeechStyleSettings settings,
            boolean profanityDetected,
            Set<String> allowedProfanity,
            String previousJson
    ) {
        Map<String, Object> style = new LinkedHashMap<>();
        if (previousJson != null) {
            try {
                JsonNode previous = objectMapper.readTree(previousJson);
                for (String field : List.of(
                        "openingPatterns", "endingPatterns", "reactionPatterns", "avoidPatterns", "punctuationStyle"
                )) {
                    if (previous.has(field)) {
                        style.put(field, previous.path(field));
                    }
                }
            } catch (RuntimeException ignored) {
                // Required settings below still form a valid minimal style profile.
            }
        }
        style.put("profanity", Map.of(
                "detected", profanityDetected,
                "enabledByUser", settings.profanityEnabled(),
                "allowedExpressions", allowedProfanity
        ));
        style.put("personalInsultAllowed", false);
        return objectMapper.writeValueAsString(style);
    }

    private Set<String> extractAllowedProfanity(String styleJson) {
        Set<String> allowed = new HashSet<>();
        try {
            JsonNode values = objectMapper.readTree(styleJson).path("profanity").path("allowedExpressions");
            values.forEach(value -> allowed.add(value.asText()));
        } catch (RuntimeException ignored) {
            return Set.of();
        }
        allowed.retainAll(SAFE_SELF_DIRECTED_PROFANITY);
        return Set.copyOf(allowed);
    }
}
