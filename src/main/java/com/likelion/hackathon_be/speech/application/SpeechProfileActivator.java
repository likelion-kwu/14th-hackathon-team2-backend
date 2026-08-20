package com.likelion.hackathon_be.speech.application;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.speech.domain.AvatarDialogue;
import com.likelion.hackathon_be.speech.domain.DialogueSituation;
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
        validateCandidate(candidate, dialogues);
        return transactionTemplate.execute(status -> activateInTransaction(userId, candidate, dialogues));
    }

    public void validateCandidate(
            SpeechProfileCandidate candidate,
            List<DialogueCandidate> dialogues
    ) {
        if (candidate == null || candidate.settings() == null || dialogues == null || dialogues.size() != 40) {
            throw new IllegalArgumentException("Exactly 40 dialogues and valid settings are required");
        }
        Map<DialogueSituation, Integer> counts = new EnumMap<>(DialogueSituation.class);
        Map<DialogueSituation, Integer> names = new EnumMap<>(DialogueSituation.class);
        Set<String> unique = new HashSet<>();
        for (DialogueCandidate dialogue : dialogues) {
            if (dialogue == null || dialogue.situation() == null || dialogue.content() == null
                    || dialogue.content().isBlank() || codePointLength(dialogue.content()) > 50
                    || !unique.add(normalize(dialogue.content()))) {
                throw new IllegalArgumentException("Dialogue batch contains an invalid or duplicate line");
            }
            counts.merge(dialogue.situation(), 1, Integer::sum);
            if (dialogue.containsUserName()) {
                names.merge(dialogue.situation(), 1, Integer::sum);
            }
            if (dialogue.containsProfanity() && !candidate.settings().profanityEnabled()) {
                throw new IllegalArgumentException("Profanity is disabled for this profile");
            }
        }
        for (DialogueSituation situation : DialogueSituation.values()) {
            if (counts.getOrDefault(situation, 0) != 5 || names.getOrDefault(situation, 0) > 1) {
                throw new IllegalArgumentException("Dialogue batch must contain five lines per situation");
            }
        }
        if (candidate.examples().size() > 20 || candidate.examples().stream().anyMatch(example ->
                example == null || example.content() == null || example.content().isBlank()
                        || codePointLength(example.content()) > 50)) {
            throw new IllegalArgumentException("Speech examples are invalid");
        }
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

    private String normalize(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }
}
