package com.likelion.hackathon_be.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "guest_sessions")
public class GuestSession {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GuestSession() {
    }

    public static GuestSession create(Long userId, String tokenHash, Instant expiresAt, Instant now) {
        GuestSession guestSession = new GuestSession();
        guestSession.id = UUID.randomUUID();
        guestSession.userId = userId;
        guestSession.tokenHash = tokenHash;
        guestSession.expiresAt = expiresAt;
        guestSession.createdAt = now;
        return guestSession;
    }

    public UUID getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
