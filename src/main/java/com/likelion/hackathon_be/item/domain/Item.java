package com.likelion.hackathon_be.item.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "items")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "item_type", nullable = false, length = 30)
    private String itemType;

    @Column(name = "asset_key", nullable = false, length = 255)
    private String assetKey;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Item() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getItemType() {
        return itemType;
    }

    public String getAssetKey() {
        return assetKey;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
