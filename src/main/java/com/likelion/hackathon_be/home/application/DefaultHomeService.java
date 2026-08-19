package com.likelion.hackathon_be.home.application;

import com.likelion.hackathon_be.avatar.domain.Avatar;
import com.likelion.hackathon_be.avatar.dto.EquippedItemResponse;
import com.likelion.hackathon_be.avatar.repository.AvatarRepository;
import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.home.dto.HomeAvatarResponse;
import com.likelion.hackathon_be.home.dto.HomePointsResponse;
import com.likelion.hackathon_be.home.dto.HomeProgressResponse;
import com.likelion.hackathon_be.home.dto.HomeResponse;
import com.likelion.hackathon_be.home.dto.HomeRoutineResponse;
import com.likelion.hackathon_be.home.dto.HomeSuccessResponse;
import com.likelion.hackathon_be.home.dto.HomeUnlockProgressResponse;
import com.likelion.hackathon_be.item.domain.Item;
import com.likelion.hackathon_be.item.domain.UserItem;
import com.likelion.hackathon_be.item.repository.ItemRepository;
import com.likelion.hackathon_be.item.repository.UserItemRepository;
import com.likelion.hackathon_be.routine.daily.domain.DailyRoutine;
import com.likelion.hackathon_be.routine.daily.repository.DailyRoutineRepository;
import com.likelion.hackathon_be.routine.daily.repository.DailySuccessRecordRepository;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.DailyRoutineStatus;
import com.likelion.hackathon_be.routine.dto.DayStatus;
import com.likelion.hackathon_be.routine.daily.application.DailyRoutineMaterializationService;
import com.likelion.hackathon_be.routine.point.repository.RoutinePointClaimRepository;
import com.likelion.hackathon_be.routine.verification.domain.RoutineVerification;
import com.likelion.hackathon_be.routine.verification.repository.RoutineVerificationRepository;
import com.likelion.hackathon_be.speech.repository.SpeechStyleProfileRepository;
import com.likelion.hackathon_be.story.application.StreakAnalysis;
import com.likelion.hackathon_be.story.application.StreakAnalysisService;
import com.likelion.hackathon_be.story.domain.StoryEpisode;
import com.likelion.hackathon_be.story.domain.UserStoryUnlock;
import com.likelion.hackathon_be.story.repository.StoryEpisodeRepository;
import com.likelion.hackathon_be.story.repository.UserStoryUnlockRepository;
import com.likelion.hackathon_be.user.domain.User;
import com.likelion.hackathon_be.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DefaultHomeService implements HomeService {
    private static final String AVATAR_IMAGE_ENDPOINT = "/api/v1/avatars/me/image";
    private static final int TODAY_CLAIM_LIMIT = 3;
    private static final int ITEM_MILESTONE_UNIT = 100;

    private final CurrentUserProvider currentUserProvider;
    private final TimeProvider timeProvider;
    private final UserRepository userRepository;
    private final AvatarRepository avatarRepository;
    private final SpeechStyleProfileRepository speechStyleProfileRepository;
    private final DailyRoutineMaterializationService materializationService;
    private final DailyRoutineRepository dailyRoutineRepository;
    private final RoutineVerificationRepository verificationRepository;
    private final RoutinePointClaimRepository pointClaimRepository;
    private final DailySuccessRecordRepository dailySuccessRecordRepository;
    private final StreakAnalysisService streakAnalysisService;
    private final UserStoryUnlockRepository userStoryUnlockRepository;
    private final StoryEpisodeRepository storyEpisodeRepository;
    private final UserItemRepository userItemRepository;
    private final ItemRepository itemRepository;

    public DefaultHomeService(
            CurrentUserProvider currentUserProvider,
            TimeProvider timeProvider,
            UserRepository userRepository,
            AvatarRepository avatarRepository,
            SpeechStyleProfileRepository speechStyleProfileRepository,
            DailyRoutineMaterializationService materializationService,
            DailyRoutineRepository dailyRoutineRepository,
            RoutineVerificationRepository verificationRepository,
            RoutinePointClaimRepository pointClaimRepository,
            DailySuccessRecordRepository dailySuccessRecordRepository,
            StreakAnalysisService streakAnalysisService,
            UserStoryUnlockRepository userStoryUnlockRepository,
            StoryEpisodeRepository storyEpisodeRepository,
            UserItemRepository userItemRepository,
            ItemRepository itemRepository
    ) {
        this.currentUserProvider = currentUserProvider;
        this.timeProvider = timeProvider;
        this.userRepository = userRepository;
        this.avatarRepository = avatarRepository;
        this.speechStyleProfileRepository = speechStyleProfileRepository;
        this.materializationService = materializationService;
        this.dailyRoutineRepository = dailyRoutineRepository;
        this.verificationRepository = verificationRepository;
        this.pointClaimRepository = pointClaimRepository;
        this.dailySuccessRecordRepository = dailySuccessRecordRepository;
        this.streakAnalysisService = streakAnalysisService;
        this.userStoryUnlockRepository = userStoryUnlockRepository;
        this.storyEpisodeRepository = storyEpisodeRepository;
        this.userItemRepository = userItemRepository;
        this.itemRepository = itemRepository;
    }

    @Override
    public HomeResponse getHome() {
        Instant serverNowInstant = timeProvider.now();
        ZoneId zone = timeProvider.serviceZone();
        OffsetDateTime serverNow = serverNowInstant.atZone(zone).toOffsetDateTime();
        LocalDate serviceDate = LocalDate.ofInstant(serverNowInstant, zone);
        Long userId = currentUserProvider.getCurrentUser().id();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        Avatar avatar = validateOnboardingAndFindAvatar(user, userId);

        materializationService.ensureMaterializedForUser(userId);

        List<DailyRoutine> dailyRoutines = dailyRoutineRepository
                .findByUserIdAndServiceDateOrderByStartTimeSnapshotAscIdAsc(userId, serviceDate);
        Map<Long, RoutineVerification> verifications = verificationsByDailyRoutineId(dailyRoutines);
        boolean successRecorded = dailySuccessRecordRepository.existsByUserIdAndServiceDate(userId, serviceDate);
        long totalEarned = pointClaimRepository.sumAmountByUserId(userId);
        MonthRange monthRange = monthRange(serviceDate, zone);
        long currentMonthEarned = pointClaimRepository.sumAmountByUserIdAndClaimedAtBetween(
                userId,
                monthRange.fromInclusive(),
                monthRange.toExclusive()
        );
        long todayClaimedCount = pointClaimRepository.countByUserIdAndServiceDate(userId, serviceDate);
        StreakAnalysis streak = streakAnalysisService.analyze(userId);

        return new HomeResponse(
                serviceDate,
                serverNow,
                avatarResponse(avatar, userId),
                progressResponse(dailyRoutines, verifications, successRecorded, serverNowInstant, zone),
                new HomePointsResponse(
                        Math.toIntExact(totalEarned),
                        Math.toIntExact(currentMonthEarned),
                        Math.toIntExact(todayClaimedCount),
                        TODAY_CLAIM_LIMIT
                ),
                new HomeSuccessResponse(
                        streak.totalSuccessDays(),
                        streak.currentStreakDays(),
                        streak.maxAchievedStreakDays()
                ),
                unlockProgressResponse(userId, totalEarned),
                routineResponses(dailyRoutines, verifications, serverNowInstant, zone)
        );
    }

    private Avatar validateOnboardingAndFindAvatar(User user, Long userId) {
        if (user.getNickname() == null || user.getNickname().isBlank()) {
            throw new BusinessException(ErrorCode.ONBOARDING_INCOMPLETE);
        }
        Avatar avatar = avatarRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ONBOARDING_INCOMPLETE));
        if (!speechStyleProfileRepository.existsByUserId(userId)) {
            throw new BusinessException(ErrorCode.ONBOARDING_INCOMPLETE);
        }
        return avatar;
    }

    private Map<Long, RoutineVerification> verificationsByDailyRoutineId(List<DailyRoutine> dailyRoutines) {
        List<Long> dailyRoutineIds = dailyRoutines.stream()
                .map(DailyRoutine::getId)
                .toList();
        return verificationRepository.findByDailyRoutineIdIn(dailyRoutineIds)
                .stream()
                .collect(Collectors.toMap(RoutineVerification::getDailyRoutineId, Function.identity()));
    }

    private HomeAvatarResponse avatarResponse(Avatar avatar, Long userId) {
        int stage = Math.max(1, Math.min(3, userStoryUnlockRepository.findMaximumAvatarStage(userId).orElse(1)));
        return new HomeAvatarResponse(
                avatar.getId(),
                avatar.getGrowthTrack().name(),
                stage,
                AVATAR_IMAGE_ENDPOINT,
                equippedItems(userId)
        );
    }

    private List<EquippedItemResponse> equippedItems(Long userId) {
        List<UserItem> equipped = userItemRepository.findAllByUserIdAndEquippedTrue(userId);
        List<Long> itemIds = equipped.stream()
                .map(UserItem::getItemId)
                .toList();
        Map<Long, Item> items = new HashMap<>();
        itemRepository.findAllById(itemIds).forEach(item -> items.put(item.getId(), item));

        List<EquippedItemResponse> responses = new ArrayList<>();
        for (Long itemId : itemIds) {
            Item item = items.get(itemId);
            if (item != null) {
                responses.add(new EquippedItemResponse(item.getId(), item.getItemType(), item.getAssetKey()));
            }
        }
        return List.copyOf(responses);
    }

    private HomeProgressResponse progressResponse(
            List<DailyRoutine> dailyRoutines,
            Map<Long, RoutineVerification> verifications,
            boolean successRecorded,
            Instant serverNow,
            ZoneId zone
    ) {
        List<DailyRoutine> eligible = eligibleRoutines(dailyRoutines);
        int totalCount = eligible.size();
        int completedCount = (int) eligible.stream()
                .filter(dailyRoutine -> verifications.containsKey(dailyRoutine.getId()))
                .count();
        return new HomeProgressResponse(
                completedCount,
                totalCount,
                percentage(completedCount, totalCount),
                dayStatus(eligible, verifications, successRecorded, serverNow, zone).name()
        );
    }

    private List<DailyRoutine> eligibleRoutines(List<DailyRoutine> dailyRoutines) {
        return dailyRoutines.stream()
                .filter(dailyRoutine -> dailyRoutine.getCategorySnapshot() != RoutineCategory.TO_DO)
                .toList();
    }

    private int percentage(int completedCount, int totalCount) {
        if (totalCount == 0) {
            return 0;
        }
        return (int) Math.round(completedCount * 100.0 / totalCount);
    }

    private DayStatus dayStatus(
            List<DailyRoutine> eligibleRoutines,
            Map<Long, RoutineVerification> verifications,
            boolean successRecorded,
            Instant serverNow,
            ZoneId zone
    ) {
        if (eligibleRoutines.isEmpty()) {
            return DayStatus.NO_ROUTINE;
        }
        if (successRecorded) {
            return DayStatus.SUCCESS;
        }
        boolean hasFailedRoutine = eligibleRoutines.stream()
                .filter(dailyRoutine -> !verifications.containsKey(dailyRoutine.getId()))
                .anyMatch(dailyRoutine -> !serverNow.isBefore(actualEndAtExclusive(dailyRoutine, zone).toInstant()));
        return hasFailedRoutine ? DayStatus.FAILED : DayStatus.IN_PROGRESS;
    }

    private HomeUnlockProgressResponse unlockProgressResponse(Long userId, long totalEarned) {
        StoryEpisode nextStory = nextLockedStory(userId);
        return new HomeUnlockProgressResponse(
                Math.toIntExact((totalEarned / ITEM_MILESTONE_UNIT + 1) * ITEM_MILESTONE_UNIT),
                nextStory == null ? null : nextStory.getEpisodeNumber(),
                nextStory == null ? null : nextStory.getRequiredStreak()
        );
    }

    private StoryEpisode nextLockedStory(Long userId) {
        List<StoryEpisode> activeEpisodes = storyEpisodeRepository.findByActiveTrueOrderByEpisodeNumberAsc();
        Set<Long> activeEpisodeIds = activeEpisodes.stream()
                .map(StoryEpisode::getId)
                .collect(Collectors.toSet());
        Set<Long> unlockedEpisodeIds = new HashSet<>(unlockedEpisodeIds(userId, activeEpisodeIds));
        return activeEpisodes.stream()
                .filter(episode -> !unlockedEpisodeIds.contains(episode.getId()))
                .findFirst()
                .orElse(null);
    }

    private Collection<Long> unlockedEpisodeIds(Long userId, Set<Long> activeEpisodeIds) {
        if (activeEpisodeIds.isEmpty()) {
            return Set.of();
        }
        return userStoryUnlockRepository.findEpisodeIdsByUserIdAndEpisodeIdIn(userId, activeEpisodeIds);
    }

    private List<HomeRoutineResponse> routineResponses(
            List<DailyRoutine> dailyRoutines,
            Map<Long, RoutineVerification> verifications,
            Instant serverNow,
            ZoneId zone
    ) {
        return dailyRoutines.stream()
                .map(dailyRoutine -> routineResponse(
                        dailyRoutine,
                        verifications.get(dailyRoutine.getId()),
                        serverNow,
                        zone
                ))
                .toList();
    }

    private HomeRoutineResponse routineResponse(
            DailyRoutine dailyRoutine,
            RoutineVerification verification,
            Instant serverNow,
            ZoneId zone
    ) {
        OffsetDateTime actualStartAt = actualStartAt(dailyRoutine, zone);
        OffsetDateTime actualEndAtExclusive = actualEndAtExclusive(dailyRoutine, zone);
        return new HomeRoutineResponse(
                dailyRoutine.getId(),
                dailyRoutine.getRoutineId(),
                dailyRoutine.getContentSnapshot(),
                dailyRoutine.getServiceDate(),
                dailyRoutine.getStartTimeSnapshot(),
                dailyRoutine.getEndTimeSnapshot(),
                actualStartAt,
                actualEndAtExclusive,
                dailyRoutine.getVerificationObjectSnapshot(),
                routineStatus(verification, serverNow, actualStartAt, actualEndAtExclusive).name(),
                verification == null ? null : verification.getVerificationType().name()
        );
    }

    private DailyRoutineStatus routineStatus(
            RoutineVerification verification,
            Instant serverNow,
            OffsetDateTime actualStartAt,
            OffsetDateTime actualEndAtExclusive
    ) {
        if (verification != null) {
            return DailyRoutineStatus.COMPLETED;
        }
        if (serverNow.isBefore(actualStartAt.toInstant())) {
            return DailyRoutineStatus.UPCOMING;
        }
        if (serverNow.isBefore(actualEndAtExclusive.toInstant())) {
            return DailyRoutineStatus.AVAILABLE;
        }
        return DailyRoutineStatus.FAILED;
    }

    private OffsetDateTime actualStartAt(DailyRoutine dailyRoutine, ZoneId zone) {
        return dailyRoutine.getServiceDate()
                .atTime(dailyRoutine.getStartTimeSnapshot())
                .atZone(zone)
                .toOffsetDateTime();
    }

    private OffsetDateTime actualEndAtExclusive(DailyRoutine dailyRoutine, ZoneId zone) {
        LocalDateTime endAtExclusive = dailyRoutine.getServiceDate()
                .atTime(dailyRoutine.getEndTimeSnapshot())
                .plusMinutes(1);
        return endAtExclusive.atZone(zone).toOffsetDateTime();
    }

    private MonthRange monthRange(LocalDate serviceDate, ZoneId zone) {
        YearMonth yearMonth = YearMonth.from(serviceDate);
        Instant fromInclusive = yearMonth.atDay(1).atStartOfDay(zone).toInstant();
        Instant toExclusive = yearMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        return new MonthRange(fromInclusive, toExclusive);
    }

    private record MonthRange(
            Instant fromInclusive,
            Instant toExclusive
    ) {
    }
}
