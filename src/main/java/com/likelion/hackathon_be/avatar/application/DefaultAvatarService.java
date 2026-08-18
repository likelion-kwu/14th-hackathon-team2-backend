package com.likelion.hackathon_be.avatar.application;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.likelion.hackathon_be.ai.AiMutationLockManager;
import com.likelion.hackathon_be.avatar.domain.Avatar;
import com.likelion.hackathon_be.avatar.domain.AvatarAssetSource;
import com.likelion.hackathon_be.avatar.domain.AvatarGrowthTrack;
import com.likelion.hackathon_be.avatar.dto.AvatarResponse;
import com.likelion.hackathon_be.avatar.dto.CreateAvatarResponse;
import com.likelion.hackathon_be.avatar.dto.EquippedItemResponse;
import com.likelion.hackathon_be.avatar.dto.RegenerateAvatarResponse;
import com.likelion.hackathon_be.avatar.dto.UpdateAvatarEquipmentRequest;
import com.likelion.hackathon_be.avatar.dto.UpdateAvatarEquipmentResponse;
import com.likelion.hackathon_be.avatar.infrastructure.AvatarFacePhotoValidator;
import com.likelion.hackathon_be.avatar.infrastructure.AvatarFaceReference;
import com.likelion.hackathon_be.avatar.infrastructure.AvatarStorage;
import com.likelion.hackathon_be.avatar.infrastructure.OpenAiAvatarSetGenerator;
import com.likelion.hackathon_be.avatar.repository.AvatarRepository;
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
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DefaultAvatarService implements AvatarService {
    private static final String IMAGE_ENDPOINT = "/api/v1/avatars/me/image";
    private static final String NEXT_STEP = "SPEECH_STYLE_SETUP";

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final AvatarRepository avatarRepository;
    private final UserStoryUnlockRepository storyUnlockRepository;
    private final UserItemRepository userItemRepository;
    private final ItemRepository itemRepository;
    private final AvatarFacePhotoValidator facePhotoValidator;
    private final OpenAiAvatarSetGenerator avatarSetGenerator;
    private final AvatarStorage avatarStorage;
    private final AiMutationLockManager lockManager;
    private final TimeProvider timeProvider;
    private final TransactionTemplate transactionTemplate;

    public DefaultAvatarService(
            CurrentUserProvider currentUserProvider,
            UserRepository userRepository,
            AvatarRepository avatarRepository,
            UserStoryUnlockRepository storyUnlockRepository,
            UserItemRepository userItemRepository,
            ItemRepository itemRepository,
            AvatarFacePhotoValidator facePhotoValidator,
            OpenAiAvatarSetGenerator avatarSetGenerator,
            AvatarStorage avatarStorage,
            AiMutationLockManager lockManager,
            TimeProvider timeProvider,
            TransactionTemplate transactionTemplate
    ) {
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.avatarRepository = avatarRepository;
        this.storyUnlockRepository = storyUnlockRepository;
        this.userItemRepository = userItemRepository;
        this.itemRepository = itemRepository;
        this.facePhotoValidator = facePhotoValidator;
        this.avatarSetGenerator = avatarSetGenerator;
        this.avatarStorage = avatarStorage;
        this.lockManager = lockManager;
        this.timeProvider = timeProvider;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public AvatarResponse getMyAvatar() {
        Long userId = currentUserProvider.getCurrentUser().id();
        Avatar avatar = findAvatar(userId);
        int stage = currentStage(userId);
        return new AvatarResponse(
                avatar.getId(),
                avatar.getGrowthTrack().name(),
                stage,
                storyUnlockRepository.findHighestUnlockedEpisodeNumber(userId).orElse(null),
                IMAGE_ENDPOINT,
                avatar.getAssetSource().name(),
                remaining(avatar),
                equippedItems(userId),
                OffsetDateTime.ofInstant(avatar.getUpdatedAt(), timeProvider.serviceZone())
        );
    }

    @Override
    public CreateAvatarResponse createAvatar(AvatarGrowthTrack growthTrack, MultipartFile facePhoto) {
        Long userId = currentUserProvider.getCurrentUser().id();
        return lockManager.withUserLock(userId, () -> createAvatarLocked(userId, growthTrack, facePhoto));
    }

    @Override
    public ResponseEntity<Resource> getMyAvatarImage() {
        Long userId = currentUserProvider.getCurrentUser().id();
        Avatar avatar = findAvatar(userId);
        Resource resource;
        try {
            resource = avatarStorage.stageResource(avatar.getAssetSetKey(), currentStage(userId));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.AVATAR_IMAGE_NOT_FOUND);
        }
        if (resource == null) {
            throw new BusinessException(ErrorCode.AVATAR_IMAGE_NOT_FOUND);
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePrivate())
                .body(resource);
    }

    @Override
    public RegenerateAvatarResponse regenerateAvatar(MultipartFile facePhoto) {
        Long userId = currentUserProvider.getCurrentUser().id();
        return lockManager.withUserLock(userId, () -> regenerateLocked(userId, facePhoto));
    }

    @Override
    public UpdateAvatarEquipmentResponse updateEquipment(UpdateAvatarEquipmentRequest request) {
        Long userId = currentUserProvider.getCurrentUser().id();
        findAvatar(userId);
        List<Long> requested = request.equippedItemIds();
        if (new HashSet<>(requested).size() != requested.size()) {
            throw new BusinessException(ErrorCode.INVALID_EQUIPMENT);
        }
        return lockManager.withUserLock(
                userId,
                () -> transactionTemplate.execute(status -> updateEquipmentInTransaction(userId, requested))
        );
    }

    private CreateAvatarResponse createAvatarLocked(Long userId, AvatarGrowthTrack track, MultipartFile facePhoto) {
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (user.getNickname() == null || user.getNickname().isBlank()) {
            throw new BusinessException(ErrorCode.NICKNAME_REQUIRED);
        }
        Optional<Avatar> existing = avatarRepository.findByUserId(userId);
        if (existing.isPresent()) {
            Avatar avatar = existing.get();
            if (avatar.getGrowthTrack() != track) {
                throw new BusinessException(ErrorCode.AVATAR_TRACK_LOCKED);
            }
            return createResponse(avatar, false, avatar.getAssetSource() == AvatarAssetSource.DEFAULT);
        }

        String assetSetKey = avatarStorage.defaultKey(track);
        AvatarAssetSource source = AvatarAssetSource.DEFAULT;
        String generatedKey = null;
        try {
            AvatarFaceReference faceReference = facePhotoValidator.validate(facePhoto);
            generatedKey = avatarStorage.storeGenerated(userId, avatarSetGenerator.generate(track, faceReference));
            assetSetKey = generatedKey;
            source = AvatarAssetSource.GENERATED;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException ignored) {
            // Initial onboarding deliberately continues with the verified DEFAULT set.
        }

        String finalAssetSetKey = assetSetKey;
        AvatarAssetSource finalSource = source;
        try {
            Avatar saved = transactionTemplate.execute(status -> {
                Optional<Avatar> concurrent = avatarRepository.findByUserIdForUpdate(userId);
                if (concurrent.isPresent()) {
                    if (concurrent.get().getGrowthTrack() != track) {
                        throw new BusinessException(ErrorCode.AVATAR_TRACK_LOCKED);
                    }
                    return concurrent.get();
                }
                return avatarRepository.save(Avatar.create(
                        userId,
                        track,
                        finalAssetSetKey,
                        finalSource,
                        timeProvider.now()
                ));
            });
            if (saved == null) {
                throw new IllegalStateException("Avatar transaction returned no result");
            }
            if (generatedKey != null && !generatedKey.equals(saved.getAssetSetKey())) {
                avatarStorage.deleteGenerated(generatedKey);
            }
            return createResponse(saved, true, saved.getAssetSource() == AvatarAssetSource.DEFAULT);
        } catch (RuntimeException exception) {
            avatarStorage.deleteGenerated(generatedKey);
            throw exception;
        }
    }

    private RegenerateAvatarResponse regenerateLocked(Long userId, MultipartFile facePhoto) {
        Avatar before = findAvatar(userId);
        ensureCanRegenerate(before);
        String newKey;
        try {
            AvatarFaceReference faceReference = facePhotoValidator.validate(facePhoto);
            newKey = avatarStorage.storeGenerated(
                    userId,
                    avatarSetGenerator.generate(before.getGrowthTrack(), faceReference)
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.AVATAR_GENERATION_FAILED);
        }

        String oldKey;
        Avatar updated;
        try {
            Object[] swap = transactionTemplate.execute(status -> {
                Avatar locked = avatarRepository.findByUserIdForUpdate(userId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.AVATAR_NOT_CONFIGURED));
                ensureCanRegenerate(locked);
                if (locked.getGrowthTrack() != before.getGrowthTrack()) {
                    throw new BusinessException(ErrorCode.AVATAR_TRACK_LOCKED);
                }
                String previous = locked.getAssetSetKey();
                locked.replaceAssetSet(newKey, AvatarAssetSource.GENERATED, true, timeProvider.now());
                return new Object[]{previous, locked};
            });
            if (swap == null) {
                throw new IllegalStateException("Avatar swap transaction returned no result");
            }
            oldKey = (String) swap[0];
            updated = (Avatar) swap[1];
        } catch (RuntimeException exception) {
            avatarStorage.deleteGenerated(newKey);
            throw exception;
        }
        avatarStorage.deleteGenerated(oldKey);
        return new RegenerateAvatarResponse(
                updated.getId(),
                updated.getGrowthTrack().name(),
                currentStage(userId),
                IMAGE_ENDPOINT,
                updated.getAssetSource().name(),
                remaining(updated),
                true
        );
    }

    private UpdateAvatarEquipmentResponse updateEquipmentInTransaction(Long userId, List<Long> requested) {
        List<UserItem> owned = userItemRepository.findAllByUserIdForUpdate(userId);
        Map<Long, UserItem> ownedByItem = new HashMap<>();
        for (UserItem userItem : owned) {
            ownedByItem.put(userItem.getItemId(), userItem);
        }
        for (Long itemId : requested) {
            if (!ownedByItem.containsKey(itemId)) {
                throw new BusinessException(ErrorCode.ITEM_NOT_OWNED);
            }
        }

        Map<Long, Item> items = new HashMap<>();
        itemRepository.findAllById(requested).forEach(item -> items.put(item.getId(), item));
        for (Long itemId : requested) {
            UserItem userItem = ownedByItem.get(itemId);
            Item item = items.get(itemId);
            if (item == null) {
                throw new BusinessException(ErrorCode.ITEM_NOT_FOUND);
            }
            if (!userItem.isEquipped() && !item.isActive()) {
                throw new BusinessException(ErrorCode.INVALID_EQUIPMENT);
            }
        }
        Set<Long> desired = Set.copyOf(requested);
        owned.forEach(userItem -> userItem.setEquipped(desired.contains(userItem.getItemId())));
        return new UpdateAvatarEquipmentResponse(toEquippedItemResponses(requested, items));
    }

    private List<EquippedItemResponse> equippedItems(Long userId) {
        List<UserItem> equipped = userItemRepository.findAllByUserIdAndEquippedTrue(userId);
        List<Long> itemIds = equipped.stream().map(UserItem::getItemId).toList();
        Map<Long, Item> items = new HashMap<>();
        itemRepository.findAllById(itemIds).forEach(item -> items.put(item.getId(), item));
        return toEquippedItemResponses(itemIds, items);
    }

    private List<EquippedItemResponse> toEquippedItemResponses(List<Long> ids, Map<Long, Item> items) {
        List<EquippedItemResponse> responses = new ArrayList<>();
        for (Long id : ids) {
            Item item = items.get(id);
            if (item != null) {
                responses.add(new EquippedItemResponse(item.getId(), item.getItemType(), item.getAssetKey()));
            }
        }
        return List.copyOf(responses);
    }

    private Avatar findAvatar(Long userId) {
        return avatarRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AVATAR_NOT_CONFIGURED));
    }

    private int currentStage(Long userId) {
        return Math.max(1, Math.min(3, storyUnlockRepository.findMaximumAvatarStage(userId).orElse(1)));
    }

    private void ensureCanRegenerate(Avatar avatar) {
        if (avatar.getRegenerationCount() >= 1) {
            throw new BusinessException(ErrorCode.AVATAR_REGENERATION_LIMIT_REACHED);
        }
    }

    private int remaining(Avatar avatar) {
        return Math.max(0, 1 - avatar.getRegenerationCount());
    }

    private CreateAvatarResponse createResponse(Avatar avatar, boolean created, boolean fallbackUsed) {
        return new CreateAvatarResponse(
                avatar.getId(),
                created,
                avatar.getGrowthTrack().name(),
                currentStage(avatar.getUserId()),
                IMAGE_ENDPOINT,
                avatar.getAssetSource().name(),
                fallbackUsed,
                remaining(avatar),
                NEXT_STEP
        );
    }
}
