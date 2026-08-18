package com.likelion.hackathon_be.competition.application;

import com.likelion.hackathon_be.common.auth.CurrentUser;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.competition.dto.CompetitionLeaderboardResponse;
import com.likelion.hackathon_be.routine.point.repository.MonthlyPointSum;
import com.likelion.hackathon_be.routine.point.repository.RoutinePointClaimRepository;
import com.likelion.hackathon_be.user.domain.User;
import com.likelion.hackathon_be.user.repository.UserRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompetitionServiceTests {

    private static final Long USER_ID = 2L;
    private static final Instant NOW = Instant.parse("2026-08-19T01:30:00Z");

    private RoutinePointClaimRepository pointClaimRepository;
    private UserRepository userRepository;
    private DefaultCompetitionService service;

    @BeforeEach
    void setUp() {
        pointClaimRepository = mock(RoutinePointClaimRepository.class);
        userRepository = mock(UserRepository.class);
        service = new DefaultCompetitionService(
                () -> new CurrentUser(USER_ID),
                new FixedTimeProvider(NOW),
                pointClaimRepository,
                userRepository
        );
    }

    @Test
    void omittedMonthUsesCurrentServiceYearMonth() {
        givenMonthlyPoints(List.of());

        CompetitionLeaderboardResponse response = service.getLeaderboard(null);

        assertThat(response.month()).isEqualTo("2026-08");
        verifyMonthlyRange("2026-08-01T00:00:00+09:00", "2026-09-01T00:00:00+09:00");
    }

    @Test
    void explicitMonthUsesAsiaSeoulMonthBoundary() {
        givenMonthlyPoints(List.of());

        CompetitionLeaderboardResponse response = service.getLeaderboard(YearMonth.of(2026, 7));

        assertThat(response.month()).isEqualTo("2026-07");
        verifyMonthlyRange("2026-07-01T00:00:00+09:00", "2026-08-01T00:00:00+09:00");
    }

    @Test
    void ranksMonthlyPointSumsWithSharedRankAndSkippedNextRank() {
        givenMonthlyPoints(List.of(
                pointSum(1L, 250L),
                pointSum(2L, 210L),
                pointSum(3L, 210L),
                pointSum(4L, 190L)
        ));
        when(userRepository.findAllById(List.of(1L, 2L, 3L, 4L)))
                .thenReturn(List.of(user(1L, "A"), user(2L, "B"), user(3L, "C"), user(4L, "D")));

        CompetitionLeaderboardResponse response = service.getLeaderboard(YearMonth.of(2026, 8));

        assertThat(response.ranking()).extracting("rank").containsExactly(1, 2, 2, 4);
        assertThat(response.ranking()).extracting("earnedPoints").containsExactly(250, 210, 210, 190);
        assertThat(response.ranking()).extracting("nickname").containsExactly("A", "B", "C", "D");
        assertThat(response.ranking()).extracting("me").containsExactly(false, true, false, false);
        assertThat(response.myRank()).isEqualTo(2);
        assertThat(response.myEarnedPoints()).isEqualTo(210);
    }

    @Test
    void secondarySortIsDelegatedToRepositoryQueryByUserIdAscendingOrder() {
        givenMonthlyPoints(List.of(pointSum(2L, 210L), pointSum(3L, 210L)));
        when(userRepository.findAllById(List.of(2L, 3L)))
                .thenReturn(List.of(user(2L, "B"), user(3L, "C")));

        CompetitionLeaderboardResponse response = service.getLeaderboard(YearMonth.of(2026, 8));

        assertThat(response.ranking()).extracting("nickname").containsExactly("B", "C");
        assertThat(response.ranking()).extracting("rank").containsExactly(1, 1);
    }

    @Test
    void authenticatedUserWithoutMonthlyClaimHasNoRankAndZeroPoints() {
        givenMonthlyPoints(List.of(pointSum(1L, 250L)));
        when(userRepository.findAllById(List.of(1L))).thenReturn(List.of(user(1L, "A")));

        CompetitionLeaderboardResponse response = service.getLeaderboard(YearMonth.of(2026, 8));

        assertThat(response.ranking()).hasSize(1);
        assertThat(response.ranking().get(0).me()).isFalse();
        assertThat(response.myRank()).isNull();
        assertThat(response.myEarnedPoints()).isZero();
    }

    @Test
    void emptyMonthlyClaimReturnsEmptyRankingAndZeroMyPoints() {
        givenMonthlyPoints(List.of());

        CompetitionLeaderboardResponse response = service.getLeaderboard(YearMonth.of(2026, 8));

        assertThat(response.ranking()).isEmpty();
        assertThat(response.myRank()).isNull();
        assertThat(response.myEarnedPoints()).isZero();
    }

    @Test
    void nullNicknameIsReturnedAsNullWithoutFakeFallback() {
        givenMonthlyPoints(List.of(pointSum(2L, 10L)));
        when(userRepository.findAllById(List.of(2L))).thenReturn(List.of(user(2L, null)));

        CompetitionLeaderboardResponse response = service.getLeaderboard(YearMonth.of(2026, 8));

        assertThat(response.ranking().get(0).nickname()).isNull();
    }

    @Test
    void usesBulkUserLookupOnlyForAggregatedUsers() {
        givenMonthlyPoints(List.of(pointSum(1L, 250L), pointSum(2L, 210L)));
        when(userRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(user(1L, "A"), user(2L, "B")));

        service.getLeaderboard(YearMonth.of(2026, 8));

        verify(userRepository).findAllById(List.of(1L, 2L));
    }

    private void givenMonthlyPoints(List<MonthlyPointSum> pointSums) {
        when(pointClaimRepository.sumMonthlyPointsByUser(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(pointSums);
        when(userRepository.findAllById(List.of())).thenReturn(List.of());
    }

    private void verifyMonthlyRange(String expectedFrom, String expectedTo) {
        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(pointClaimRepository).sumMonthlyPointsByUser(fromCaptor.capture(), toCaptor.capture());
        assertThat(fromCaptor.getValue()).isEqualTo(Instant.from(java.time.OffsetDateTime.parse(expectedFrom)));
        assertThat(toCaptor.getValue()).isEqualTo(Instant.from(java.time.OffsetDateTime.parse(expectedTo)));
    }

    private MonthlyPointSum pointSum(Long userId, Long earnedPoints) {
        return new TestMonthlyPointSum(userId, earnedPoints);
    }

    private User user(Long id, String nickname) {
        User user = User.createGuest(NOW);
        setField(user, "id", id);
        setField(user, "nickname", nickname);
        return user;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record TestMonthlyPointSum(Long userId, Long earnedPoints) implements MonthlyPointSum {

        @Override
        public Long getUserId() {
            return userId;
        }

        @Override
        public Long getEarnedPoints() {
            return earnedPoints;
        }
    }

    private record FixedTimeProvider(Instant now) implements TimeProvider {

        @Override
        public LocalDate todayServiceDate() {
            return LocalDate.ofInstant(now, serviceZone());
        }

        @Override
        public ZoneId serviceZone() {
            return ZoneId.of("Asia/Seoul");
        }
    }
}
