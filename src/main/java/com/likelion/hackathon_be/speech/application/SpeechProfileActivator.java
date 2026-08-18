package com.likelion.hackathon_be.speech.application;

import java.time.Instant;
import java.util.List;

import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.speech.domain.AvatarDialogue;
import com.likelion.hackathon_be.speech.domain.SpeechStyleExample;
import com.likelion.hackathon_be.speech.domain.SpeechStyleProfile;
import com.likelion.hackathon_be.speech.repository.AvatarDialogueRepository;
import com.likelion.hackathon_be.speech.repository.SpeechStyleExampleRepository;
import com.likelion.hackathon_be.speech.repository.SpeechStyleProfileRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpeechProfileActivator {
    private final SpeechStyleProfileRepository profileRepository;
    private final SpeechStyleExampleRepository exampleRepository;
    private final AvatarDialogueRepository dialogueRepository;
    private final TimeProvider timeProvider;
    private final TransactionTemplate transactionTemplate;

    public SpeechProfileActivator(
            SpeechStyleProfileRepository profileRepository,
            SpeechStyleExampleRepository exampleRepository,
            AvatarDialogueRepository dialogueRepository,
            TimeProvider timeProvider,
            TransactionTemplate transactionTemplate
    ) {
        this.profileRepository = profileRepository;
        this.exampleRepository = exampleRepository;
        this.dialogueRepository = dialogueRepository;
        this.timeProvider = timeProvider;
        this.transactionTemplate = transactionTemplate;
    }

    public SpeechStyleProfile activate(
            Long userId,
            SpeechProfileCandidate candidate,
            List<DialogueCandidate> dialogues
    ) {
        if (dialogues.size() != 40) {
            throw new IllegalArgumentException("Exactly 40 dialogues are required");
        }
        return transactionTemplate.execute(status -> activateInTransaction(userId, candidate, dialogues));
    }

    private SpeechStyleProfile activateInTransaction(
            Long userId,
            SpeechProfileCandidate candidate,
            List<DialogueCandidate> dialogues
    ) {
        Instant now = timeProvider.now();
        SpeechStyleSettings settings = candidate.settings();
        SpeechStyleProfile profile = profileRepository.findByUserIdForUpdate(userId).orElse(null);
        if (profile == null) {
            profile = profileRepository.save(SpeechStyleProfile.create(
                    userId,
                    candidate.sourceType(),
                    candidate.presetCode(),
                    settings.speechLevel(),
                    settings.sentenceLength(),
                    settings.directness(),
                    settings.warmth(),
                    settings.playfulness(),
                    settings.emotionalIntensity(),
                    candidate.styleJson(),
                    candidate.profanityDetected(),
                    settings.profanityEnabled(),
                    candidate.validMessageCount(),
                    now
            ));
        } else {
            exampleRepository.deleteAllByProfileId(profile.getId());
            dialogueRepository.deleteAllByProfileId(profile.getId());
            profile.replace(
                    candidate.sourceType(),
                    candidate.presetCode(),
                    settings.speechLevel(),
                    settings.sentenceLength(),
                    settings.directness(),
                    settings.warmth(),
                    settings.playfulness(),
                    settings.emotionalIntensity(),
                    candidate.styleJson(),
                    candidate.profanityDetected(),
                    settings.profanityEnabled(),
                    candidate.validMessageCount(),
                    now
            );
        }

        Long profileId = profile.getId();
        List<SpeechStyleExample> examples = candidate.examples().stream()
                .limit(20)
                .map(example -> SpeechStyleExample.create(
                        profileId,
                        example.category(),
                        example.sourceType(),
                        example.content(),
                        now
                ))
                .toList();
        exampleRepository.saveAll(examples);
        dialogueRepository.saveAll(dialogues.stream()
                .map(dialogue -> AvatarDialogue.create(
                        profileId,
                        dialogue.situation(),
                        dialogue.content(),
                        dialogue.containsUserName(),
                        dialogue.containsProfanity(),
                        now
                ))
                .toList());
        return profile;
    }
}
