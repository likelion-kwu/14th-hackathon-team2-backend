package com.likelion.hackathon_be.speech.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "avatar_dialogues")
public class AvatarDialogue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "profile_id", nullable = false)
    private Long profileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "situation", nullable = false, length = 40)
    private DialogueSituation situation;

    @Column(name = "content", nullable = false, length = 50)
    private String content;

    @Column(name = "contains_user_name", nullable = false)
    private boolean containsUserName;

    @Column(name = "contains_profanity", nullable = false)
    private boolean containsProfanity;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "use_count", nullable = false)
    private int useCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AvatarDialogue() {
    }

    public static AvatarDialogue create(
            Long profileId,
            DialogueSituation situation,
            String content,
            boolean containsUserName,
            boolean containsProfanity,
            Instant now
    ) {
        AvatarDialogue dialogue = new AvatarDialogue();
        dialogue.profileId = profileId;
        dialogue.situation = situation;
        dialogue.content = content;
        dialogue.containsUserName = containsUserName;
        dialogue.containsProfanity = containsProfanity;
        dialogue.useCount = 0;
        dialogue.createdAt = now;
        return dialogue;
    }

    public void recordUse(Instant now) {
        this.lastUsedAt = now;
        this.useCount++;
    }

    public Long getId() {
        return id;
    }

    public Long getProfileId() {
        return profileId;
    }

    public DialogueSituation getSituation() {
        return situation;
    }

    public String getContent() {
        return content;
    }

    public boolean isContainsUserName() {
        return containsUserName;
    }

    public boolean isContainsProfanity() {
        return containsProfanity;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public int getUseCount() {
        return useCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
