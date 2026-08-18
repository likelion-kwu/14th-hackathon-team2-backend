package com.likelion.hackathon_be.avatar.infrastructure;

import java.util.List;
import java.util.Set;

import com.likelion.hackathon_be.avatar.repository.AvatarRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvatarStorageStartupRecoveryTests {

    @Test
    void startupRecoveryProtectsEveryAssetSetReferencedByTheDatabase() {
        AvatarRepository avatarRepository = mock(AvatarRepository.class);
        AvatarStorage avatarStorage = mock(AvatarStorage.class);
        when(avatarRepository.findAllAssetSetKeys()).thenReturn(List.of(
                "defaults/skin",
                "generated/101/00000000-0000-0000-0000-000000000001"
        ));
        AvatarStorageStartupRecovery recovery = new AvatarStorageStartupRecovery(avatarRepository, avatarStorage);

        recovery.recoverUnreferencedGeneratedSets();

        verify(avatarStorage).cleanupUnreferencedGeneratedSets(Set.of(
                "defaults/skin",
                "generated/101/00000000-0000-0000-0000-000000000001"
        ));
    }
}
