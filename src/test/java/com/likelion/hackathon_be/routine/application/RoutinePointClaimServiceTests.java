package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.auth.CurrentUser;
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
import com.likelion.hackathon_be.routine.point.domain.RoutinePointClaim;
import com.likelion.hackathon_be.routine.point.repository.RoutinePointClaimRepository;
import com.likelion.hackathon_be.routine.verification.domain.RoutineVerification;
import com.likelion.hackathon_be.routine.verification.domain.VerificationType;
import com.likelion.hackathon_be.routine.verification.repository.RoutineVerificationRepository;
import com.likelion.hackathon_be.user.domain.User;
import com.likelion.hackathon_be.user.repository.UserRepository;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutinePointClaimServiceTests {

    private static final Long USER_ID = 10L;
    private static final Long OTHER_USER_ID = 20L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);
    private static final LocalDate YESTERDAY = TODAY.minusDays(1);
    private static final Instant NOW = Instant.parse("2026-08-19T01:00:00Z");

    private UserRepository userRepository;
    private DailyRoutineRepository dailyRoutineRepository;
    private RoutineVerificationRepository verificationRepository;
    private RoutinePointClaimRepository pointClaimRepository;
    private ItemRepository itemRepository;
    private UserItemRepository userItemRepository;
    private ItemUnlockRecordRepository itemUnlockRecordRepository;
    private ItemUnlockSelector itemUnlockSelector;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        dailyRoutineRepository = mock(DailyRoutineRepository.class);
        verificationRepository = mock(RoutineVerificationRepository.class);
        pointClaimRepository = mock(RoutinePointClaimRepository.class);
        itemRepository = mock(ItemRepository.class);
        userItemRepository = mock(UserItemRepository.class);
        itemUnlockRecordRepository = mock(ItemUnlockRecordRepository.class);
        itemUnlockSelector = candidates -> candidates.stream().findFirst();

        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(pointClaimRepository.findByDailyRoutineId(anyLong())).thenReturn(Optional.empty());
        when(pointClaimRepository.countByUserIdAndServiceDate(USER_ID, TODAY)).thenReturn(0L);
        when(pointClaimRepository.saveAndFlush(any(RoutinePointClaim.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(pointClaimRepository.sumAmountByUserId(USER_ID)).thenReturn(5L);
        when(itemUnlockRecordRepository.findRequiredPointsByUserIdAndRequiredPointsLessThanEqual(anyLong(), anyInt()))
                .thenReturn(List.of());
        when(itemRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of());
        when(userItemRepository.findItemIdsByUserId(USER_ID)).thenReturn(List.of());
        when(userItemRepository.save(any(UserItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(itemUnlockRecordRepository.save(any(ItemUnlockRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void checkCompletedRoutineClaimAwardsFivePoints() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, TODAY);
        givenDailyRoutine(dailyRoutine);
        givenVerification(1L, VerificationType.CHECK);

        PointClaimResponse response = service().claimPoint(1L);

        ArgumentCaptor<RoutinePointClaim> captor = ArgumentCaptor.forClass(RoutinePointClaim.class);
        verify(pointClaimRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo((short) 5);
        assertThat(captor.getValue().getClaimedAt()).isEqualTo(NOW);
        assertThat(response.awardedPoints()).isEqualTo(5);
        assertThat(response.todayClaimedCount()).isEqualTo(1);
        assertThat(response.todayClaimLimit()).isEqualTo(3);
        assertThat(response.totalEarnedPoints()).isEqualTo(5);
        assertThat(response.itemUnlock()).isNull();
    }

    @Test
    void photoCompletedRoutineClaimAwardsTenPoints() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, TODAY);
        givenDailyRoutine(dailyRoutine);
        givenVerification(1L, VerificationType.PHOTO);
        when(pointClaimRepository.sumAmountByUserId(USER_ID)).thenReturn(10L);

        PointClaimResponse response = service().claimPoint(1L);

        ArgumentCaptor<RoutinePointClaim> captor = ArgumentCaptor.forClass(RoutinePointClaim.class);
        verify(pointClaimRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualTo((short) 10);
        assertThat(response.awardedPoints()).isEqualTo(10);
        assertThat(response.totalEarnedPoints()).isEqualTo(10);
    }

    @Test
    void routineWithoutVerificationCannotBeClaimed() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, TODAY);
        givenDailyRoutine(dailyRoutine);
        when(verificationRepository.findByDailyRoutineId(1L)).thenReturn(Optional.empty());

        assertBusinessError(() -> service().claimPoint(1L), ErrorCode.ROUTINE_NOT_COMPLETED);

        verify(pointClaimRepository, never()).saveAndFlush(any());
    }

    @Test
    void otherUsersDailyRoutineReturnsNotFound() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, OTHER_USER_ID, RoutineCategory.SKIN, TODAY);
        givenDailyRoutine(dailyRoutine);

        assertBusinessError(() -> service().claimPoint(1L), ErrorCode.DAILY_ROUTINE_NOT_FOUND);

        verify(pointClaimRepository, never()).saveAndFlush(any());
    }

    @Test
    void pastServiceDateClaimIsExpired() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, YESTERDAY);
        givenDailyRoutine(dailyRoutine);
        givenVerification(1L, VerificationType.CHECK);

        assertBusinessError(() -> service().claimPoint(1L), ErrorCode.POINT_CLAIM_EXPIRED);

        verify(pointClaimRepository, never()).saveAndFlush(any());
    }

    @Test
    void duplicateClaimReturnsPointAlreadyClaimed() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, TODAY);
        givenDailyRoutine(dailyRoutine);
        givenVerification(1L, VerificationType.CHECK);
        when(pointClaimRepository.findByDailyRoutineId(1L))
                .thenReturn(Optional.of(RoutinePointClaim.create(USER_ID, 1L, (short) 5, NOW)));

        assertBusinessError(() -> service().claimPoint(1L), ErrorCode.POINT_ALREADY_CLAIMED);

        verify(pointClaimRepository, never()).saveAndFlush(any());
    }

    @Test
    void fourthClaimInSameServiceDateIsRejected() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, TODAY);
        givenDailyRoutine(dailyRoutine);
        givenVerification(1L, VerificationType.CHECK);
        when(pointClaimRepository.countByUserIdAndServiceDate(USER_ID, TODAY)).thenReturn(3L);

        assertBusinessError(() -> service().claimPoint(1L), ErrorCode.POINT_CLAIM_LIMIT_REACHED);

        verify(pointClaimRepository, never()).saveAndFlush(any());
    }

    @Test
    void thirdClaimInSameServiceDateSucceeds() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, TODAY);
        givenDailyRoutine(dailyRoutine);
        givenVerification(1L, VerificationType.CHECK);
        when(pointClaimRepository.countByUserIdAndServiceDate(USER_ID, TODAY)).thenReturn(2L);

        PointClaimResponse response = service().claimPoint(1L);

        assertThat(response.todayClaimedCount()).isEqualTo(3);
        verify(pointClaimRepository).saveAndFlush(any(RoutinePointClaim.class));
    }

    @Test
    void todoCompletedRoutineIsNotPointClaimEligible() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.TO_DO, TODAY);
        givenDailyRoutine(dailyRoutine);

        assertBusinessError(() -> service().claimPoint(1L), ErrorCode.POINT_CLAIM_NOT_ELIGIBLE);

        verify(verificationRepository, never()).findByDailyRoutineId(1L);
        verify(pointClaimRepository, never()).saveAndFlush(any());
    }

    @Test
    void checkClaimReachingHundredPointsUnlocksOneItemWithoutDeduction() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, TODAY);
        Item item = item(31L, "lion sunglasses", "ACCESSORY", "items/accessory/31", true);
        givenDailyRoutine(dailyRoutine);
        givenVerification(1L, VerificationType.CHECK);
        when(pointClaimRepository.sumAmountByUserId(USER_ID)).thenReturn(100L);
        when(itemRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(item));

        PointClaimResponse response = service().claimPoint(1L);

        ArgumentCaptor<UserItem> userItemCaptor = ArgumentCaptor.forClass(UserItem.class);
        ArgumentCaptor<ItemUnlockRecord> recordCaptor = ArgumentCaptor.forClass(ItemUnlockRecord.class);
        verify(userItemRepository).save(userItemCaptor.capture());
        verify(itemUnlockRecordRepository).save(recordCaptor.capture());
        assertThat(userItemCaptor.getValue().getItemId()).isEqualTo(31L);
        assertThat(userItemCaptor.getValue().isEquipped()).isFalse();
        assertThat(recordCaptor.getValue().getRequiredPoints()).isEqualTo(100);
        assertThat(recordCaptor.getValue().getItemId()).isEqualTo(31L);
        assertThat(response.totalEarnedPoints()).isEqualTo(100);
        assertThat(response.itemUnlock()).isEqualTo(new ItemUnlockResponse(
                true,
                100,
                response.itemUnlock().item()
        ));
        assertThat(response.itemUnlock().item().id()).isEqualTo(31L);
    }

    @Test
    void photoClaimReachingHundredPointsUnlocksItem() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, TODAY);
        Item item = item(31L, "lion sunglasses", "ACCESSORY", "items/accessory/31", true);
        givenDailyRoutine(dailyRoutine);
        givenVerification(1L, VerificationType.PHOTO);
        when(pointClaimRepository.sumAmountByUserId(USER_ID)).thenReturn(100L);
        when(itemRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(item));

        PointClaimResponse response = service().claimPoint(1L);

        assertThat(response.awardedPoints()).isEqualTo(10);
        assertThat(response.itemUnlock().milestonePoints()).isEqualTo(100);
    }

    @Test
    void alreadyProcessedMilestoneDoesNotUnlockAgain() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, TODAY);
        Item item = item(31L, "lion sunglasses", "ACCESSORY", "items/accessory/31", true);
        givenDailyRoutine(dailyRoutine);
        givenVerification(1L, VerificationType.CHECK);
        when(pointClaimRepository.sumAmountByUserId(USER_ID)).thenReturn(105L);
        when(itemUnlockRecordRepository.findRequiredPointsByUserIdAndRequiredPointsLessThanEqual(USER_ID, 100))
                .thenReturn(List.of(100));
        when(itemRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(item));

        PointClaimResponse response = service().claimPoint(1L);

        assertThat(response.itemUnlock()).isNull();
        verify(userItemRepository, never()).save(any(UserItem.class));
        verify(itemUnlockRecordRepository, never()).save(any(ItemUnlockRecord.class));
    }

    @Test
    void twoHundredMilestoneCanBeRecoveredWhenHundredWasProcessed() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, TODAY);
        Item item = item(32L, "lion cap", "ACCESSORY", "items/accessory/32", true);
        givenDailyRoutine(dailyRoutine);
        givenVerification(1L, VerificationType.CHECK);
        when(pointClaimRepository.sumAmountByUserId(USER_ID)).thenReturn(200L);
        when(itemUnlockRecordRepository.findRequiredPointsByUserIdAndRequiredPointsLessThanEqual(USER_ID, 200))
                .thenReturn(List.of(100));
        when(itemRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(item));

        PointClaimResponse response = service().claimPoint(1L);

        ArgumentCaptor<ItemUnlockRecord> recordCaptor = ArgumentCaptor.forClass(ItemUnlockRecord.class);
        verify(itemUnlockRecordRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getRequiredPoints()).isEqualTo(200);
        assertThat(response.itemUnlock().milestonePoints()).isEqualTo(200);
    }

    @Test
    void ownedItemsAreNotSelectedAsUnlockCandidates() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, TODAY);
        Item owned = item(31L, "owned", "ACCESSORY", "items/accessory/31", true);
        Item unowned = item(32L, "unowned", "ACCESSORY", "items/accessory/32", true);
        givenDailyRoutine(dailyRoutine);
        givenVerification(1L, VerificationType.CHECK);
        when(pointClaimRepository.sumAmountByUserId(USER_ID)).thenReturn(100L);
        when(itemRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(owned, unowned));
        when(userItemRepository.findItemIdsByUserId(USER_ID)).thenReturn(List.of(31L));

        PointClaimResponse response = service().claimPoint(1L);

        assertThat(response.itemUnlock().item().id()).isEqualTo(32L);
    }

    @Test
    void noUnownedActiveItemRecordsProcessedMilestoneWithoutUserItem() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, TODAY);
        Item owned = item(31L, "owned", "ACCESSORY", "items/accessory/31", true);
        givenDailyRoutine(dailyRoutine);
        givenVerification(1L, VerificationType.CHECK);
        when(pointClaimRepository.sumAmountByUserId(USER_ID)).thenReturn(100L);
        when(itemRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(owned));
        when(userItemRepository.findItemIdsByUserId(USER_ID)).thenReturn(List.of(31L));

        PointClaimResponse response = service().claimPoint(1L);

        ArgumentCaptor<ItemUnlockRecord> recordCaptor = ArgumentCaptor.forClass(ItemUnlockRecord.class);
        verify(itemUnlockRecordRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getRequiredPoints()).isEqualTo(100);
        assertThat(recordCaptor.getValue().getItemId()).isNull();
        assertThat(response.itemUnlock()).isNull();
        verify(userItemRepository, never()).save(any(UserItem.class));
    }

    @Test
    void pointClaimUniqueViolationMapsToAlreadyClaimed() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, TODAY);
        givenDailyRoutine(dailyRoutine);
        givenVerification(1L, VerificationType.CHECK);
        when(pointClaimRepository.saveAndFlush(any(RoutinePointClaim.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertBusinessError(() -> service().claimPoint(1L), ErrorCode.POINT_ALREADY_CLAIMED);
    }

    @Test
    void locksUserBeforeTargetRoutineAndClaimInsert() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, TODAY);
        givenDailyRoutine(dailyRoutine);
        givenVerification(1L, VerificationType.CHECK);

        service().claimPoint(1L);

        InOrder inOrder = inOrder(userRepository, dailyRoutineRepository, pointClaimRepository);
        inOrder.verify(userRepository).findByIdForUpdate(USER_ID);
        inOrder.verify(dailyRoutineRepository).findById(1L);
        inOrder.verify(pointClaimRepository).saveAndFlush(any(RoutinePointClaim.class));
    }

    @Test
    void itemUnlockFailurePropagatesSoPointClaimTransactionCanRollback() {
        DailyRoutine dailyRoutine = dailyRoutine(1L, USER_ID, RoutineCategory.SKIN, TODAY);
        Item item = item(31L, "lion sunglasses", "ACCESSORY", "items/accessory/31", true);
        givenDailyRoutine(dailyRoutine);
        givenVerification(1L, VerificationType.CHECK);
        when(pointClaimRepository.sumAmountByUserId(USER_ID)).thenReturn(100L);
        when(itemRepository.findByActiveTrueOrderByIdAsc()).thenReturn(List.of(item));
        when(itemUnlockRecordRepository.save(any(ItemUnlockRecord.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate milestone"));

        assertThatThrownBy(() -> service().claimPoint(1L))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(pointClaimRepository).saveAndFlush(any(RoutinePointClaim.class));
        verify(userItemRepository, never()).save(any(UserItem.class));
    }

    private DefaultRoutinePointService service() {
        return new DefaultRoutinePointService(
                () -> new CurrentUser(USER_ID),
                new FixedTimeProvider(NOW),
                userRepository,
                dailyRoutineRepository,
                verificationRepository,
                pointClaimRepository,
                itemRepository,
                userItemRepository,
                itemUnlockRecordRepository,
                itemUnlockSelector
        );
    }

    private void givenDailyRoutine(DailyRoutine dailyRoutine) {
        when(dailyRoutineRepository.findById(dailyRoutine.getId())).thenReturn(Optional.of(dailyRoutine));
    }

    private void givenVerification(Long dailyRoutineId, VerificationType verificationType) {
        when(verificationRepository.findByDailyRoutineId(dailyRoutineId))
                .thenReturn(Optional.of(RoutineVerification.create(dailyRoutineId, verificationType, NOW)));
    }

    private void assertBusinessError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private User user(Long id) {
        User user = User.createGuest(NOW);
        setField(user, "id", id);
        return user;
    }

    private DailyRoutine dailyRoutine(
            Long id,
            Long userId,
            RoutineCategory category,
            LocalDate serviceDate
    ) {
        DailyRoutine dailyRoutine = newInstance(DailyRoutine.class);
        setField(dailyRoutine, "id", id);
        setField(dailyRoutine, "routineId", 100L + id);
        setField(dailyRoutine, "userId", userId);
        setField(dailyRoutine, "serviceDate", serviceDate);
        setField(dailyRoutine, "categorySnapshot", category);
        setField(dailyRoutine, "contentSnapshot", "routine");
        setField(dailyRoutine, "verificationObjectSnapshot", "cup");
        setField(dailyRoutine, "createdAt", NOW);
        setField(dailyRoutine, "updatedAt", NOW);
        return dailyRoutine;
    }

    private Item item(Long id, String name, String itemType, String assetKey, boolean active) {
        Item item = newInstance(Item.class);
        setField(item, "id", id);
        setField(item, "name", name);
        setField(item, "itemType", itemType);
        setField(item, "assetKey", assetKey);
        setField(item, "active", active);
        setField(item, "createdAt", NOW);
        return item;
    }

    private <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record FixedTimeProvider(Instant now) implements TimeProvider {

        @Override
        public LocalDate todayServiceDate() {
            return TODAY;
        }

        @Override
        public ZoneId serviceZone() {
            return ZoneId.of("Asia/Seoul");
        }
    }
}
