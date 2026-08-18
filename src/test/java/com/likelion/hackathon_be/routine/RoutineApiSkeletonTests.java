package com.likelion.hackathon_be.routine;

import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.routine.application.NotImplementedRoutinePointService;
import com.likelion.hackathon_be.routine.application.NotImplementedRoutineService;
import com.likelion.hackathon_be.routine.application.NotImplementedRoutineVerificationService;
import com.likelion.hackathon_be.routine.domain.DayOfWeek;
import com.likelion.hackathon_be.routine.domain.RepeatType;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.CreateRoutineRequest;
import com.likelion.hackathon_be.routine.dto.DailyRoutineStatus;
import com.likelion.hackathon_be.routine.dto.DayStatus;
import com.likelion.hackathon_be.routine.dto.PointClaimResponse;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutineApiSkeletonTests {

    @Test
    void routineSkeletonUses501Placeholder() {
        NotImplementedRoutineService service = new NotImplementedRoutineService();

        assertThatThrownBy(service::getRoutines)
                .isInstanceOfSatisfying(FeatureNotImplementedException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.NOT_IMPLEMENTED));
    }

    @Test
    void verificationAndPointSkeletonsUse501Placeholder() {
        NotImplementedRoutineVerificationService verificationService = new NotImplementedRoutineVerificationService();
        NotImplementedRoutinePointService pointService = new NotImplementedRoutinePointService();

        assertThatThrownBy(() -> verificationService.verifyCheck(1L))
                .isInstanceOf(FeatureNotImplementedException.class);
        assertThatThrownBy(() -> pointService.claimPoint(1L))
                .isInstanceOf(FeatureNotImplementedException.class);
    }

    @Test
    void createRoutineRequestDoesNotAcceptClientUserId() {
        assertThat(Arrays.stream(CreateRoutineRequest.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly(
                        "category",
                        "content",
                        "scheduledDate",
                        "startTime",
                        "endTime",
                        "repeatType",
                        "daysOfWeek",
                        "verificationObject"
                );
    }

    @Test
    void pointClaimResponseDoesNotAcceptAmountRequestContract() {
        assertThat(PointClaimResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly(
                        "dailyRoutineId",
                        "awardedPoints",
                        "todayClaimedCount",
                        "todayClaimLimit",
                        "totalEarnedPoints",
                        "itemUnlock"
                );
    }

    @Test
    void routineApiEnumsMatchSpec() {
        assertThat(RoutineCategory.values()).containsExactly(
                RoutineCategory.SKIN,
                RoutineCategory.WELL_BEING,
                RoutineCategory.HEALTH_FIT,
                RoutineCategory.DIET,
                RoutineCategory.TO_DO
        );
        assertThat(RepeatType.values()).containsExactly(RepeatType.DAILY, RepeatType.DAYS_OF_WEEK, RepeatType.ONCE);
        assertThat(DayOfWeek.values()).containsExactly(
                DayOfWeek.MON,
                DayOfWeek.TUE,
                DayOfWeek.WED,
                DayOfWeek.THU,
                DayOfWeek.FRI,
                DayOfWeek.SAT,
                DayOfWeek.SUN
        );
        assertThat(DailyRoutineStatus.values()).containsExactly(
                DailyRoutineStatus.UPCOMING,
                DailyRoutineStatus.AVAILABLE,
                DailyRoutineStatus.COMPLETED,
                DailyRoutineStatus.FAILED
        );
        assertThat(DayStatus.values()).containsExactly(
                DayStatus.NO_ROUTINE,
                DayStatus.IN_PROGRESS,
                DayStatus.SUCCESS,
                DayStatus.FAILED
        );
    }
}
