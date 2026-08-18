package com.likelion.hackathon_be.competition.application;

import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.competition.dto.CompetitionLeaderboardResponse;
import com.likelion.hackathon_be.competition.dto.CompetitionRankingEntryResponse;
import com.likelion.hackathon_be.routine.point.repository.MonthlyPointSum;
import com.likelion.hackathon_be.routine.point.repository.RoutinePointClaimRepository;
import com.likelion.hackathon_be.user.domain.User;
import com.likelion.hackathon_be.user.repository.UserRepository;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultCompetitionService implements CompetitionService {

    private final CurrentUserProvider currentUserProvider;
    private final TimeProvider timeProvider;
    private final RoutinePointClaimRepository pointClaimRepository;
    private final UserRepository userRepository;

    public DefaultCompetitionService(
            CurrentUserProvider currentUserProvider,
            TimeProvider timeProvider,
            RoutinePointClaimRepository pointClaimRepository,
            UserRepository userRepository
    ) {
        this.currentUserProvider = currentUserProvider;
        this.timeProvider = timeProvider;
        this.pointClaimRepository = pointClaimRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CompetitionLeaderboardResponse getLeaderboard(YearMonth month) {
        Long currentUserId = currentUserProvider.getCurrentUser().id();
        YearMonth targetMonth = month == null ? currentYearMonth() : month;
        Instant monthStart = targetMonth.atDay(1)
                .atStartOfDay(timeProvider.serviceZone())
                .toInstant();
        Instant nextMonthStart = targetMonth.plusMonths(1)
                .atDay(1)
                .atStartOfDay(timeProvider.serviceZone())
                .toInstant();

        List<MonthlyPointSum> pointSums = pointClaimRepository.sumMonthlyPointsByUser(monthStart, nextMonthStart);
        Map<Long, User> usersById = userRepository.findAllById(pointSums.stream()
                        .map(MonthlyPointSum::getUserId)
                        .toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        RankingResult rankingResult = ranking(pointSums, usersById, currentUserId);
        return new CompetitionLeaderboardResponse(
                targetMonth.toString(),
                rankingResult.entries(),
                rankingResult.myRank(),
                rankingResult.myEarnedPoints()
        );
    }

    private YearMonth currentYearMonth() {
        return YearMonth.from(timeProvider.todayServiceDate());
    }

    private RankingResult ranking(
            List<MonthlyPointSum> pointSums,
            Map<Long, User> usersById,
            Long currentUserId
    ) {
        List<CompetitionRankingEntryResponse> entries = new ArrayList<>();
        Integer myRank = null;
        int myEarnedPoints = 0;
        Long previousPoints = null;
        int rank = 0;

        for (int index = 0; index < pointSums.size(); index++) {
            MonthlyPointSum pointSum = pointSums.get(index);
            long earnedPoints = pointSum.getEarnedPoints();
            if (previousPoints == null || earnedPoints != previousPoints) {
                rank = index + 1;
            }
            previousPoints = earnedPoints;

            boolean me = pointSum.getUserId().equals(currentUserId);
            if (me) {
                myRank = rank;
                myEarnedPoints = Math.toIntExact(earnedPoints);
            }
            User user = usersById.get(pointSum.getUserId());
            entries.add(new CompetitionRankingEntryResponse(
                    rank,
                    user == null ? null : user.getNickname(),
                    Math.toIntExact(earnedPoints),
                    me
            ));
        }

        return new RankingResult(entries, myRank, myEarnedPoints);
    }

    private record RankingResult(
            List<CompetitionRankingEntryResponse> entries,
            Integer myRank,
            int myEarnedPoints
    ) {
    }
}
