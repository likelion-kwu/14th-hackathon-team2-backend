package com.likelion.hackathon_be.story.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_story_unlocks")
public class UserStoryUnlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "episode_id", nullable = false)
    private Long episodeId;

    @Column(name = "unlocked_at", nullable = false)
    private Instant unlockedAt;

    protected UserStoryUnlock() {
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getEpisodeId() {
        return episodeId;
    }

    public Instant getUnlockedAt() {
        return unlockedAt;
    }
}
