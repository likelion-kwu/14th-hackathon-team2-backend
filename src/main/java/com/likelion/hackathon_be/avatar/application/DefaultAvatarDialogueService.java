package com.likelion.hackathon_be.avatar.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.likelion.hackathon_be.avatar.dto.AvatarDialogueSelectionResponse;
import com.likelion.hackathon_be.avatar.dto.SelectAvatarDialogueRequest;
import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.speech.domain.AvatarDialogue;
import com.likelion.hackathon_be.speech.domain.SpeechStyleProfile;
import com.likelion.hackathon_be.speech.repository.AvatarDialogueRepository;
import com.likelion.hackathon_be.speech.repository.SpeechStyleProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DefaultAvatarDialogueService implements AvatarDialogueService {
    private final CurrentUserProvider currentUserProvider;
    private final SpeechStyleProfileRepository profileRepository;
    private final AvatarDialogueRepository dialogueRepository;
    private final TimeProvider timeProvider;
    private final TransactionTemplate transactionTemplate;

    public DefaultAvatarDialogueService(
            CurrentUserProvider currentUserProvider,
            SpeechStyleProfileRepository profileRepository,
            AvatarDialogueRepository dialogueRepository,
            TimeProvider timeProvider,
            TransactionTemplate transactionTemplate
    ) {
        this.currentUserProvider = currentUserProvider;
        this.profileRepository = profileRepository;
        this.dialogueRepository = dialogueRepository;
        this.timeProvider = timeProvider;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public AvatarDialogueSelectionResponse selectDialogue(SelectAvatarDialogueRequest request) {
        Long userId = currentUserProvider.getCurrentUser().id();
        return transactionTemplate.execute(status -> selectInTransaction(userId, request));
    }

    private AvatarDialogueSelectionResponse selectInTransaction(
            Long userId,
            SelectAvatarDialogueRequest request
    ) {
        SpeechStyleProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STYLE_NOT_FOUND));
        List<AvatarDialogue> candidates = dialogueRepository.findAllByProfileIdAndSituation(
                profile.getId(),
                request.situation()
        );
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.DIALOGUE_GENERATION_FAILED);
        }

        Instant now = timeProvider.now();
        Instant cutoff = now.minus(24, ChronoUnit.HOURS);
        List<AvatarDialogue> unusedRecently = candidates.stream()
                .filter(dialogue -> dialogue.getLastUsedAt() == null || dialogue.getLastUsedAt().isBefore(cutoff))
                .toList();
        AvatarDialogue selected;
        if (!unusedRecently.isEmpty()) {
            selected = unusedRecently.get(ThreadLocalRandom.current().nextInt(unusedRecently.size()));
        } else {
            selected = candidates.stream()
                    .min(Comparator.comparing(
                            AvatarDialogue::getLastUsedAt,
                            Comparator.nullsFirst(Comparator.naturalOrder())
                    ))
                    .orElseThrow();
        }
        selected.recordUse(now);
        return new AvatarDialogueSelectionResponse(
                selected.getId(),
                selected.getSituation().name(),
                selected.getContent()
        );
    }
}
