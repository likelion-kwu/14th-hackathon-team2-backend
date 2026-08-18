package com.likelion.hackathon_be.avatar.application;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import com.likelion.hackathon_be.ai.AiMutationLockManager;
import com.likelion.hackathon_be.avatar.domain.Avatar;
import com.likelion.hackathon_be.avatar.domain.AvatarAssetSource;
import com.likelion.hackathon_be.avatar.domain.AvatarGrowthTrack;
import com.likelion.hackathon_be.avatar.dto.CreateAvatarResponse;
import com.likelion.hackathon_be.avatar.dto.RegenerateAvatarResponse;
import com.likelion.hackathon_be.avatar.dto.UpdateAvatarEquipmentRequest;
import com.likelion.hackathon_be.avatar.dto.UpdateAvatarEquipmentResponse;
import com.likelion.hackathon_be.avatar.infrastructure.AvatarFacePhotoValidator;
import com.likelion.hackathon_be.avatar.infrastructure.AvatarGenerationException;
import com.likelion.hackathon_be.avatar.infrastructure.AvatarStorage;
import com.likelion.hackathon_be.avatar.infrastructure.OpenAiAvatarSetGenerator;
import com.likelion.hackathon_be.avatar.repository.AvatarRepository;
import com.likelion.hackathon_be.common.auth.CurrentUser;
import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.item.domain.Item;
import com.likelion.hackathon_be.item.domain.UserItem;
import com.likelion.hackathon_be.item.repository.ItemRepository;
import com.likelion.hackathon_be.item.repository.UserItemRepository;
import com.likelion.hackathon_be.story.repository.UserStoryUnlockRepository;
import com.likelion.hackathon_be.user.domain.User;
import com.likelion.hackathon_be.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAvatarServiceTests {
    private static final Long USER_ID = 101L;
    private static final Long AVATAR_ID = 12L;
    private static final Instant NOW = Instant.parse("2026-08-19T03:00:00Z");

    private UserRepository userRepository;
    private AvatarRepository avatarRepository;
    private UserStoryUnlockRepository storyUnlockRepository;
    private UserItemRepository userItemRepository;
    private ItemRepository itemRepository;
    private AvatarFacePhotoValidator facePhotoValidator;
    private OpenAiAvatarSetGenerator avatarSetGenerator;
    private AvatarStorage avatarStorage;
    private DefaultAvatarService service;

    @BeforeEach
    void setUp() throws Exception {
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        userRepository = mock(UserRepository.class);
        avatarRepository = mock(AvatarRepository.class);
        storyUnlockRepository = mock(UserStoryUnlockRepository.class);
        userItemRepository = mock(UserItemRepository.class);
        itemRepository = mock(ItemRepository.class);
        facePhotoValidator = mock(AvatarFacePhotoValidator.class);
        avatarSetGenerator = mock(OpenAiAvatarSetGenerator.class);
        avatarStorage = mock(AvatarStorage.class);

        when(currentUserProvider.getCurrentUser()).thenReturn(new CurrentUser(USER_ID));
        when(storyUnlockRepository.findMaximumAvatarStage(USER_ID)).thenReturn(Optional.empty());

        TimeProvider timeProvider = new FixedTimeProvider();
        TransactionTemplate transactionTemplate = new TransactionTemplate(new NoOpTransactionManager());
        service = new DefaultAvatarService(
                currentUserProvider,
                userRepository,
                avatarRepository,
                storyUnlockRepository,
                userItemRepository,
                itemRepository,
                facePhotoValidator,
                avatarSetGenerator,
                avatarStorage,
                new AiMutationLockManager(),
                timeProvider,
                transactionTemplate
        );

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithNickname()));
    }

    @Test
    void initialGenerationFailureFallsBackToTrackDefaultWithoutBlockingOnboarding() throws Exception {
        when(avatarRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(avatarRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.empty());
        when(avatarStorage.defaultKey(AvatarGrowthTrack.SKIN)).thenReturn("defaults/skin");
        when(avatarSetGenerator.generate(AvatarGrowthTrack.SKIN, null))
                .thenThrow(new AvatarGenerationException("provider unavailable"));
        when(avatarRepository.save(any(Avatar.class))).thenAnswer(invocation -> {
            Avatar saved = invocation.getArgument(0);
            setField(saved, "id", AVATAR_ID);
            return saved;
        });

        CreateAvatarResponse response = service.createAvatar(AvatarGrowthTrack.SKIN, null);

        assertThat(response.created()).isTrue();
        assertThat(response.assetSource()).isEqualTo("DEFAULT");
        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.regenerationRemaining()).isEqualTo(1);
        assertThat(response.nextStep()).isEqualTo("SPEECH_STYLE_SETUP");
        verify(avatarRepository).save(any(Avatar.class));
        verify(avatarStorage, never()).storeGenerated(any(), any());
    }

    @Test
    void regenerationFailurePreservesExistingSetAndDoesNotConsumeAllowance() throws Exception {
        Avatar avatar = avatar("generated/101/original", AvatarAssetSource.GENERATED);
        when(avatarRepository.findByUserId(USER_ID)).thenReturn(Optional.of(avatar));
        when(avatarSetGenerator.generate(AvatarGrowthTrack.SKIN, null))
                .thenThrow(new AvatarGenerationException("provider unavailable"));

        assertThatThrownBy(() -> service.regenerateAvatar(null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AVATAR_GENERATION_FAILED));

        assertThat(avatar.getAssetSetKey()).isEqualTo("generated/101/original");
        assertThat(avatar.getAssetSource()).isEqualTo(AvatarAssetSource.GENERATED);
        assertThat(avatar.getRegenerationCount()).isZero();
        verify(avatarRepository, never()).findByUserIdForUpdate(USER_ID);
        verify(avatarStorage, never()).deleteGenerated(any());
    }

    @Test
    void successfulRegenerationAtomicallySwapsWholeSetConsumesAllowanceAndDeletesOnlyOldSet() throws Exception {
        String oldKey = "generated/101/00000000-0000-0000-0000-000000000001";
        String newKey = "generated/101/00000000-0000-0000-0000-000000000002";
        Avatar avatar = avatar(oldKey, AvatarAssetSource.GENERATED);
        List<byte[]> stages = List.of(new byte[]{1}, new byte[]{2}, new byte[]{3});
        when(avatarRepository.findByUserId(USER_ID)).thenReturn(Optional.of(avatar));
        when(avatarRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(avatar));
        when(avatarSetGenerator.generate(AvatarGrowthTrack.SKIN, null)).thenReturn(stages);
        when(avatarStorage.storeGenerated(USER_ID, stages)).thenReturn(newKey);

        RegenerateAvatarResponse response = service.regenerateAvatar(null);

        assertThat(avatar.getAssetSetKey()).isEqualTo(newKey);
        assertThat(avatar.getAssetSource()).isEqualTo(AvatarAssetSource.GENERATED);
        assertThat(avatar.getRegenerationCount()).isEqualTo((short) 1);
        assertThat(response.replaced()).isTrue();
        assertThat(response.regenerationRemaining()).isZero();
        verify(avatarStorage).deleteGenerated(oldKey);
        verify(avatarStorage, never()).deleteGenerated(newKey);
    }

    @Test
    void equipmentAllowsMultipleOwnedActiveItems() throws Exception {
        UserItem hat = UserItem.create(USER_ID, 201L, NOW);
        UserItem glasses = UserItem.create(USER_ID, 202L, NOW);
        Item hatItem = item(201L, true);
        Item glassesItem = item(202L, true);
        when(avatarRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(avatar("defaults/skin", AvatarAssetSource.DEFAULT)));
        when(userItemRepository.findAllByUserIdForUpdate(USER_ID)).thenReturn(List.of(hat, glasses));
        when(itemRepository.findAllById(List.of(201L, 202L))).thenReturn(List.of(hatItem, glassesItem));

        UpdateAvatarEquipmentResponse response = service.updateEquipment(
                new UpdateAvatarEquipmentRequest(List.of(201L, 202L))
        );

        assertThat(hat.isEquipped()).isTrue();
        assertThat(glasses.isEquipped()).isTrue();
        assertThat(response.equippedItems()).extracting("itemId").containsExactly(201L, 202L);
    }

    @Test
    void equipmentRejectsDuplicateItemIdsBeforeMutation() throws Exception {
        UserItem owned = UserItem.create(USER_ID, 201L, NOW);
        when(avatarRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(avatar("defaults/skin", AvatarAssetSource.DEFAULT)));

        assertThatThrownBy(() -> service.updateEquipment(
                new UpdateAvatarEquipmentRequest(List.of(201L, 201L))
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_EQUIPMENT));

        assertThat(owned.isEquipped()).isFalse();
        verify(userItemRepository, never()).findAllByUserIdForUpdate(USER_ID);
    }

    @Test
    void equipmentRejectsItemNotOwnedByCurrentUser() throws Exception {
        UserItem owned = UserItem.create(USER_ID, 201L, NOW);
        when(avatarRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(avatar("defaults/skin", AvatarAssetSource.DEFAULT)));
        when(userItemRepository.findAllByUserIdForUpdate(USER_ID)).thenReturn(List.of(owned));

        assertThatThrownBy(() -> service.updateEquipment(
                new UpdateAvatarEquipmentRequest(List.of(999L))
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ITEM_NOT_OWNED));

        assertThat(owned.isEquipped()).isFalse();
    }

    @Test
    void equipmentRejectsNewlyEquippedInactiveItem() throws Exception {
        UserItem owned = UserItem.create(USER_ID, 201L, NOW);
        Item inactiveItem = item(201L, false);
        when(avatarRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(avatar("defaults/skin", AvatarAssetSource.DEFAULT)));
        when(userItemRepository.findAllByUserIdForUpdate(USER_ID)).thenReturn(List.of(owned));
        when(itemRepository.findAllById(List.of(201L))).thenReturn(List.of(inactiveItem));

        assertThatThrownBy(() -> service.updateEquipment(
                new UpdateAvatarEquipmentRequest(List.of(201L))
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_EQUIPMENT));

        assertThat(owned.isEquipped()).isFalse();
    }

    @Test
    void equipmentKeepsAlreadyEquippedInactiveItem() throws Exception {
        UserItem owned = UserItem.create(USER_ID, 201L, NOW);
        owned.setEquipped(true);
        Item inactiveItem = item(201L, false);
        when(avatarRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(avatar("defaults/skin", AvatarAssetSource.DEFAULT)));
        when(userItemRepository.findAllByUserIdForUpdate(USER_ID)).thenReturn(List.of(owned));
        when(itemRepository.findAllById(List.of(201L))).thenReturn(List.of(inactiveItem));

        UpdateAvatarEquipmentResponse response = service.updateEquipment(
                new UpdateAvatarEquipmentRequest(List.of(201L))
        );

        assertThat(owned.isEquipped()).isTrue();
        assertThat(response.equippedItems()).extracting("itemId").containsExactly(201L);
    }

    private User userWithNickname() throws Exception {
        User user = User.createGuest(NOW);
        setField(user, "id", USER_ID);
        setField(user, "nickname", "테스터");
        return user;
    }

    private Avatar avatar(String assetSetKey, AvatarAssetSource source) throws Exception {
        Avatar avatar = Avatar.create(USER_ID, AvatarGrowthTrack.SKIN, assetSetKey, source, NOW);
        setField(avatar, "id", AVATAR_ID);
        return avatar;
    }

    private Item item(Long id, boolean active) {
        Item item = mock(Item.class);
        when(item.getId()).thenReturn(id);
        when(item.getItemType()).thenReturn("ACCESSORY");
        when(item.getAssetKey()).thenReturn("items/" + id + ".png");
        when(item.isActive()).thenReturn(active);
        return item;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class FixedTimeProvider implements TimeProvider {
        @Override
        public Instant now() {
            return NOW;
        }

        @Override
        public LocalDate todayServiceDate() {
            return LocalDate.of(2026, 8, 19);
        }

        @Override
        public ZoneId serviceZone() {
            return ZoneId.of("Asia/Seoul");
        }
    }

    private static final class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
