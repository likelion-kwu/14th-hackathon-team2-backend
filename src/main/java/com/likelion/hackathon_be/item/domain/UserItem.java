package com.likelion.hackathon_be.item.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_items")
public class UserItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "equipped", nullable = false)
    private boolean equipped;

    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt;

    protected UserItem() {
    }

    public static UserItem create(Long userId, Long itemId, Instant acquiredAt) {
        UserItem userItem = new UserItem();
        userItem.userId = userId;
        userItem.itemId = itemId;
        userItem.equipped = false;
        userItem.acquiredAt = acquiredAt;
        return userItem;
    }

    public void setEquipped(boolean equipped) {
        this.equipped = equipped;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getItemId() {
        return itemId;
    }

    public boolean isEquipped() {
        return equipped;
    }

    public Instant getAcquiredAt() {
        return acquiredAt;
    }
}
