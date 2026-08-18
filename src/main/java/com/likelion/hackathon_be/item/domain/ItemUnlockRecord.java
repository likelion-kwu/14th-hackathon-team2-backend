package com.likelion.hackathon_be.item.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "item_unlock_records")
public class ItemUnlockRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "required_points", nullable = false)
    private int requiredPoints;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ItemUnlockRecord() {
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public int getRequiredPoints() {
        return requiredPoints;
    }

    public Long getItemId() {
        return itemId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
