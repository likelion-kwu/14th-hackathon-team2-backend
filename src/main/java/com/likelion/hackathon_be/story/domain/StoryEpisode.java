package com.likelion.hackathon_be.story.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "story_episodes")
public class StoryEpisode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "episode_number", nullable = false)
    private int episodeNumber;

    @Column(name = "required_streak", nullable = false)
    private int requiredStreak;

    @Column(name = "avatar_stage", nullable = false)
    private short avatarStage;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected StoryEpisode() {
    }

    public Long getId() {
        return id;
    }

    public int getEpisodeNumber() {
        return episodeNumber;
    }

    public int getRequiredStreak() {
        return requiredStreak;
    }

    public short getAvatarStage() {
        return avatarStage;
    }

    public boolean isActive() {
        return active;
    }
}
