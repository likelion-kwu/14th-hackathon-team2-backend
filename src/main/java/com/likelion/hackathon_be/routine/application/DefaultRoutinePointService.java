package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.item.application.ItemUnlockSelector;
import com.likelion.hackathon_be.item.domain.Item;
import com.likelion.hackathon_be.item.domain.ItemUnlockRecord;
import com.likelion.hackathon_be.item.domain.UserItem;
import com.likelion.hackathon_be.item.repository.ItemRepository;
import com.likelion.hackathon_be.item.repository.ItemUnlockRecordRepository;
import com.likelion.hackathon_be.item.repository.UserItemRepository;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.ItemUnlockResponse;
import com.likelion.hackathon_be.routine.dto.PointClaimResponse;
import com.likelion.hackathon_be.routine.dto.UnlockedItemResponse;
import com.likelion.hackathon_be.routine.point.domain.RoutinePointClaim;
import com.likelion.hackathon_be.routine.point.repository.RoutinePointClaimRepository;
import com.likelion.hackathon_be.routine.verification.domain.RoutineVerification;
import com.likelion.hackathon_be.routine.verification.domain.VerificationType;
import com.likelion.hackathon_be.routine.verification.repository.RoutineVerificationRepository;
import com.likelion.hackathon_be.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultRoutinePointService implements RoutinePointService {

    private static final int DAILY_CLAIM_LIMIT = 3;
    private static final int ITEM_UNLOCK_POINT_UNIT = 100;
    private static final short PHOTO_REWARD_POINTS = 10;
    private static final short CHECK_REWARD_POINTS = 5;

    private final CurrentUserProvider currentUserProvider;
    private final TimeProvider timeProvider;
    private final UserRepository userRepository;
    private final DailyRoutineRepository dailyRoutineRepository;
    private final RoutineVerificationRepository verificationRepository;
    private final RoutinePointClaimRepository pointClaimRepository;
    private final ItemRepository itemRepository;
    private final UserItemRepository userItemRepository;
    private final ItemUnlockRecordRepository itemUnlockRecordRepository;
    private final ItemUnlockSelector itemUnlockSelector;

    public DefaultRoutinePointService(
            CurrentUserProvider currentUserProvider,
            TimeProvider timeProvider,
            UserRepository userRepository,
            DailyRoutineRepository dailyRoutineRepository,
            RoutineVerificationRepository verificationRepository,
            RoutinePointClaimRepository pointClaimRepository,
            ItemRepository itemRepository,
            UserItemRepository userItemRepository,
            ItemUnlockRecordRepository itemUnlockRecordRepository,
            ItemUnlockSelector itemUnlockSelector
    ) {
        this.currentUserProvider = currentUserProvider;
        this.timeProvider = timeProvider;
        this.userRepository = userRepository;
        this.dailyRoutineRepository = dailyRoutineRepository;
        this.verificationRepository = verificationRepository;
        this.pointClaimRepository = pointClaimRepository;
        this.itemRepository = itemRepository;
        this.userItemRepository = userItemRepository;
        this.itemUnlockRecordRepository = itemUnlockRecordRepository;
        this.itemUnlockSelector = itemUnlockSelector;
    }

    @Override
    @Transactional
    public PointClaimResponse claimPoint(Long dailyRoutineId) {
        Instant claimedAt = timeProvider.now();
        LocalDate currentServiceDate = timeProvider.todayServiceDate();
        Long userId = currentUserProvider.getCurrentUser().id();

        lockUser(userId);

        DailyRoutine dailyRoutine = dailyRoutineRepository.findById(dailyRoutineId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DAILY_ROUTINE_NOT_FOUND));
        validateOwnership(dailyRoutine, userId);
        validatePointEligibleCategory(dailyRoutine);

        RoutineVerification verification = verificationRepository.findByDailyRoutineId(dailyRoutineId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROUTINE_NOT_COMPLETED));

        validateServiceDate(dailyRoutine, currentServiceDate);
        validateNotClaimed(dailyRoutineId);

        long todayClaimCount = pointClaimRepository.countByUserIdAndServiceDate(userId, dailyRoutine.getServiceDate());
        if (todayClaimCount >= DAILY_CLAIM_LIMIT) {
            throw new BusinessException(ErrorCode.POINT_CLAIM_LIMIT_REACHED);
        }

        short awardedPoints = rewardPoints(verification.getVerificationType());
        savePointClaim(userId, dailyRoutineId, awardedPoints, claimedAt);

        long totalEarnedPoints = pointClaimRepository.sumAmountByUserId(userId);
        ItemUnlockResponse itemUnlock = processItemUnlocks(userId, totalEarnedPoints, claimedAt);

        return new PointClaimResponse(
                dailyRoutineId,
                awardedPoints,
                Math.toIntExact(todayClaimCount + 1),
                DAILY_CLAIM_LIMIT,
                Math.toIntExact(totalEarnedPoints),
                itemUnlock
        );
    }

    private void lockUser(Long userId) {
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private void validateOwnership(DailyRoutine dailyRoutine, Long userId) {
        if (!dailyRoutine.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.DAILY_ROUTINE_NOT_FOUND);
        }
    }

    private void validatePointEligibleCategory(DailyRoutine dailyRoutine) {
        if (dailyRoutine.getCategorySnapshot() == RoutineCategory.TO_DO) {
            throw new BusinessException(ErrorCode.POINT_CLAIM_NOT_ELIGIBLE);
        }
    }

    private void validateServiceDate(DailyRoutine dailyRoutine, LocalDate currentServiceDate) {
        if (!dailyRoutine.getServiceDate().equals(currentServiceDate)) {
            throw new BusinessException(ErrorCode.POINT_CLAIM_EXPIRED);
        }
    }

    private void validateNotClaimed(Long dailyRoutineId) {
        if (pointClaimRepository.findByDailyRoutineId(dailyRoutineId).isPresent()) {
            throw new BusinessException(ErrorCode.POINT_ALREADY_CLAIMED);
        }
    }

    private short rewardPoints(VerificationType verificationType) {
        return verificationType == VerificationType.PHOTO ? PHOTO_REWARD_POINTS : CHECK_REWARD_POINTS;
    }

    private void savePointClaim(
            Long userId,
            Long dailyRoutineId,
            short awardedPoints,
            Instant claimedAt
    ) {
        try {
            pointClaimRepository.saveAndFlush(
                    RoutinePointClaim.create(userId, dailyRoutineId, awardedPoints, claimedAt)
            );
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.POINT_ALREADY_CLAIMED);
        }
    }

    private ItemUnlockResponse processItemUnlocks(
            Long userId,
            long totalEarnedPoints,
            Instant processedAt
    ) {
        int highestReachedMilestone = highestReachedMilestone(totalEarnedPoints);
        if (highestReachedMilestone < ITEM_UNLOCK_POINT_UNIT) {
            return null;
        }

        Set<Integer> processedMilestones = new HashSet<>(
                itemUnlockRecordRepository.findRequiredPointsByUserIdAndRequiredPointsLessThanEqual(
                        userId,
                        highestReachedMilestone
                )
        );
        List<Item> activeItems = itemRepository.findByActiveTrueOrderByIdAsc();
        Set<Long> ownedItemIds = new HashSet<>(userItemRepository.findItemIdsByUserId(userId));

        ItemUnlockResponse lastNewlyUnlocked = null;
        for (int milestone = ITEM_UNLOCK_POINT_UNIT;
             milestone <= highestReachedMilestone;
             milestone += ITEM_UNLOCK_POINT_UNIT) {
            if (processedMilestones.contains(milestone)) {
                continue;
            }
            Optional<Item> selectedItem = selectUnownedActiveItem(activeItems, ownedItemIds);
            Long selectedItemId = selectedItem.map(Item::getId).orElse(null);
            itemUnlockRecordRepository.save(ItemUnlockRecord.create(
                    userId,
                    milestone,
                    selectedItemId,
                    processedAt
            ));
            if (selectedItem.isPresent()) {
                Item item = selectedItem.get();
                userItemRepository.save(UserItem.create(userId, item.getId(), processedAt));
                ownedItemIds.add(item.getId());
                lastNewlyUnlocked = toItemUnlockResponse(milestone, item);
            }
        }

        return lastNewlyUnlocked;
    }

    private int highestReachedMilestone(long totalEarnedPoints) {
        return Math.toIntExact(totalEarnedPoints / ITEM_UNLOCK_POINT_UNIT * ITEM_UNLOCK_POINT_UNIT);
    }

    private Optional<Item> selectUnownedActiveItem(List<Item> activeItems, Set<Long> ownedItemIds) {
        List<Item> candidates = activeItems.stream()
                .filter(item -> !ownedItemIds.contains(item.getId()))
                .toList();
        return itemUnlockSelector.select(candidates);
    }

    private ItemUnlockResponse toItemUnlockResponse(int milestone, Item item) {
        return new ItemUnlockResponse(
                true,
                milestone,
                new UnlockedItemResponse(
                        item.getId(),
                        item.getName(),
                        item.getItemType(),
                        item.getAssetKey()
                )
        );
    }
}
