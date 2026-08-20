package com.likelion.hackathon_be.speech.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import com.likelion.hackathon_be.avatar.dto.AvatarDialogueSelectionResponse;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.speech.domain.AvatarDialogue;
import com.likelion.hackathon_be.speech.domain.DialogueSituation;
import com.likelion.hackathon_be.speech.domain.SpeechStyleProfile;
import com.likelion.hackathon_be.speech.repository.AvatarDialogueRepository;
import com.likelion.hackathon_be.speech.repository.SpeechStyleProfileRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class AvatarDialogueSelector {
    private final SpeechStyleProfileRepository profileRepository;
    private final AvatarDialogueRepository dialogueRepository;
    private final TimeProvider timeProvider;
    private final TransactionTemplate transactionTemplate;

    public AvatarDialogueSelector(
            SpeechStyleProfileRepository profileRepository,
            AvatarDialogueRepository dialogueRepository,
            TimeProvider timeProvider,
            TransactionTemplate transactionTemplate
    ) {
        this.profileRepository = profileRepository;
        this.dialogueRepository = dialogueRepository;
        this.timeProvider = timeProvider;
        this.transactionTemplate = transactionTemplate;
    }

    public AvatarDialogueSelectionResponse selectForUser(Long userId, DialogueSituation situation) {
        return transactionTemplate.execute(status -> selectInTransaction(userId, situation));
    }

    private AvatarDialogueSelectionResponse selectInTransaction(Long userId, DialogueSituation situation) {
        SpeechStyleProfile profile = profileRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SPEECH_STYLE_NOT_CONFIGURED));
        List<AvatarDialogue> candidates = dialogueRepository.findAllByProfileIdAndSituation(
                profile.getId(),
                situation
        );
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.DIALOGUE_GENERATION_FAILED);
        }

        Instant now = timeProvider.now();
        Instant cutoff = now.minus(24, ChronoUnit.HOURS);
        List<AvatarDialogue> unusedRecently = candidates.stream()
                .filter(dialogue -> dialogue.getLastUsedAt() == null || !dialogue.getLastUsedAt().isAfter(cutoff))
                .toList();
        AvatarDialogue selected;
        if (!unusedRecently.isEmpty()) {
            selected = unusedRecently.get(ThreadLocalRandom.current().nextInt(unusedRecently.size()));
        } else {
            selected = candidates.stream()
                    .min(Comparator.comparing(
                            AvatarDialogue::getLastUsedAt,
                            Comparator.nullsFirst(Comparator.naturalOrder())
                    ).thenComparing(AvatarDialogue::getId))
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
