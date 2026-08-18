package com.likelion.hackathon_be.speech;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.likelion.hackathon_be.avatar.domain.Avatar;
import com.likelion.hackathon_be.avatar.domain.AvatarAssetSource;
import com.likelion.hackathon_be.avatar.domain.AvatarGrowthTrack;
import com.likelion.hackathon_be.avatar.repository.AvatarRepository;
import com.likelion.hackathon_be.speech.application.DialogueCandidate;
import com.likelion.hackathon_be.speech.application.SpeechProfileActivator;
import com.likelion.hackathon_be.speech.application.SpeechProfileCandidate;
import com.likelion.hackathon_be.speech.application.SpeechStyleSettings;
import com.likelion.hackathon_be.speech.domain.DialogueSituation;
import com.likelion.hackathon_be.speech.domain.SpeechSourceType;
import com.likelion.hackathon_be.speech.repository.AvatarDialogueRepository;
import com.likelion.hackathon_be.speech.repository.SpeechStyleProfileRepository;
import com.likelion.hackathon_be.user.domain.User;
import com.likelion.hackathon_be.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.avatar.storage-root=${java.io.tmpdir}/godsaeng-lion/test-avatar"
})
@Testcontainers(disabledWithoutDocker = true)
class PartBPersistenceIntegrationTests {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    UserRepository userRepository;

    @Autowired
    AvatarRepository avatarRepository;

    @Autowired
    SpeechStyleProfileRepository profileRepository;

    @Autowired
    AvatarDialogueRepository dialogueRepository;

    @Autowired
    SpeechProfileActivator profileActivator;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    void jsonbProfileAndFortyDialoguesReplaceAtomicallyAndCascade() {
        User user = userRepository.save(User.createGuest(Instant.now()));
        SpeechProfileCandidate candidate = candidate(SpeechStyleSettings.calm());

        profileActivator.activate(user.getId(), candidate, dialogues("첫"));
        Long profileId = profileRepository.findByUserId(user.getId()).orElseThrow().getId();
        assertThat(dialogueRepository.countByProfileId(profileId)).isEqualTo(40);
        assertThat(profileRepository.findById(profileId).orElseThrow().getStyleJson())
                .contains("openingPatterns")
                .doesNotContain("speechLevel", "sentenceLength", "directness");

        profileActivator.activate(user.getId(), candidate, dialogues("새"));
        assertThat(profileRepository.findByUserId(user.getId()).orElseThrow().getId()).isEqualTo(profileId);
        assertThat(dialogueRepository.findAllByProfileId(profileId))
                .hasSize(40)
                .allSatisfy(dialogue -> assertThat(dialogue.getContent()).startsWith("새"));

        profileRepository.deleteById(profileId);
        profileRepository.flush();
        assertThat(dialogueRepository.countByProfileId(profileId)).isZero();
    }

    @Test
    void avatarAssetSetSwapsWithoutAddingSchemaColumns() {
        User user = userRepository.save(User.createGuest(Instant.now()));
        Avatar avatar = avatarRepository.save(Avatar.create(
                user.getId(),
                AvatarGrowthTrack.SKIN,
                "defaults/skin",
                AvatarAssetSource.DEFAULT,
                Instant.now()
        ));

        transactionTemplate.executeWithoutResult(status -> avatarRepository.findByUserIdForUpdate(user.getId())
                .orElseThrow()
                .replaceAssetSet("generated/1/test", AvatarAssetSource.GENERATED, true, Instant.now()));

        Avatar updated = avatarRepository.findById(avatar.getId()).orElseThrow();
        assertThat(updated.getAssetSetKey()).isEqualTo("generated/1/test");
        assertThat(updated.getAssetSource()).isEqualTo(AvatarAssetSource.GENERATED);
        assertThat(updated.getRegenerationCount()).isEqualTo((short) 1);
    }

    private SpeechProfileCandidate candidate(SpeechStyleSettings settings) {
        return new SpeechProfileCandidate(
                SpeechSourceType.PRESET,
                "CALM",
                settings,
                "{\"openingPatterns\":[\"근데\"],\"personalInsultAllowed\":false}",
                false,
                null,
                List.of()
        );
    }

    private List<DialogueCandidate> dialogues(String prefix) {
        List<DialogueCandidate> result = new ArrayList<>();
        for (DialogueSituation situation : DialogueSituation.values()) {
            for (int index = 0; index < 5; index++) {
                result.add(new DialogueCandidate(
                        situation,
                        prefix + " 대사 " + situation.ordinal() + "-" + index,
                        false,
                        false
                ));
            }
        }
        return result;
    }
}
