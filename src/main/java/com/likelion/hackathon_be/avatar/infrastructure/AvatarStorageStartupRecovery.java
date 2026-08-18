package com.likelion.hackathon_be.avatar.infrastructure;

import java.util.HashSet;

import com.likelion.hackathon_be.avatar.repository.AvatarRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
class AvatarStorageStartupRecovery {
    private final AvatarRepository avatarRepository;
    private final AvatarStorage avatarStorage;

    AvatarStorageStartupRecovery(AvatarRepository avatarRepository, AvatarStorage avatarStorage) {
        this.avatarRepository = avatarRepository;
        this.avatarStorage = avatarStorage;
    }

    @PostConstruct
    void recoverUnreferencedGeneratedSets() {
        avatarStorage.cleanupUnreferencedGeneratedSets(new HashSet<>(avatarRepository.findAllAssetSetKeys()));
    }
}
