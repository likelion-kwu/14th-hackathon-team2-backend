package com.likelion.hackathon_be.avatar.domain;

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
@Table(name = "avatars")
public class Avatar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "growth_track", nullable = false, length = 20)
    private AvatarGrowthTrack growthTrack;

    @Column(name = "asset_set_key", nullable = false, length = 255)
    private String assetSetKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_source", nullable = false, length = 20)
    private AvatarAssetSource assetSource;

    @Column(name = "regeneration_count", nullable = false)
    private short regenerationCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Avatar() {
    }

    public static Avatar create(
            Long userId,
            AvatarGrowthTrack growthTrack,
            String assetSetKey,
            AvatarAssetSource assetSource,
            Instant now
    ) {
        Avatar avatar = new Avatar();
        avatar.userId = userId;
        avatar.growthTrack = growthTrack;
        avatar.assetSetKey = assetSetKey;
        avatar.assetSource = assetSource;
        avatar.regenerationCount = 0;
        avatar.createdAt = now;
        avatar.updatedAt = now;
        return avatar;
    }

    public void replaceAssetSet(String assetSetKey, AvatarAssetSource assetSource, boolean regeneration, Instant now) {
        this.assetSetKey = assetSetKey;
        this.assetSource = assetSource;
        if (regeneration) {
            this.regenerationCount++;
        }
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public AvatarGrowthTrack getGrowthTrack() {
        return growthTrack;
    }

    public String getAssetSetKey() {
        return assetSetKey;
    }

    public AvatarAssetSource getAssetSource() {
        return assetSource;
    }

    public short getRegenerationCount() {
        return regenerationCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
