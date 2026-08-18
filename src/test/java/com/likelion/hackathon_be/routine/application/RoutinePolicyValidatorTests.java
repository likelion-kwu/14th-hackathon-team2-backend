package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.routine.domain.DayOfWeek;
import com.likelion.hackathon_be.routine.domain.RepeatType;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.CreateRoutineRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutinePolicyValidatorTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    private RoutineCatalogService routineCatalogService;
    private RoutinePolicyValidator validator;

    @BeforeEach
    void setUp() {
        routineCatalogService = mock(RoutineCatalogService.class);
        validator = new RoutinePolicyValidator(routineCatalogService);
        when(routineCatalogService.supportsVerificationObject("CUP")).thenReturn(true);
    }

    @Test
    void validatesAndNormalizesDaysOfWeekRoutine() {
        RoutinePolicyValidator.ValidatedRoutine result = validator.validate(new CreateRoutineRequest(
                RoutineCategory.WELL_BEING,
                "  물 마시기  ",
                null,
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                RepeatType.DAYS_OF_WEEK,
                List.of(DayOfWeek.FRI, DayOfWeek.MON),
                "CUP"
        ), TODAY);

        assertThat(result.content()).isEqualTo("물 마시기");
        assertThat(result.daysOfWeek()).containsExactly(DayOfWeek.MON, DayOfWeek.FRI);
    }

    @Test
    void rejectsCategoryAndRepeatTypeMismatch() {
        assertError(request(RoutineCategory.TO_DO, RepeatType.DAILY, null, List.of()),
                ErrorCode.INVALID_REPEAT_TYPE_FOR_CATEGORY);
    }

    @Test
    void rejectsMissingOrDuplicateDaysOfWeek() {
        assertError(request(RoutineCategory.SKIN, RepeatType.DAYS_OF_WEEK, null, List.of()),
                ErrorCode.INVALID_REPEAT_DAYS);
        assertError(request(
                        RoutineCategory.SKIN,
                        RepeatType.DAYS_OF_WEEK,
                        null,
                        List.of(DayOfWeek.MON, DayOfWeek.MON)
                ),
                ErrorCode.INVALID_REPEAT_DAYS);
    }

    @Test
    void rejectsRepeatDaysForDailyRoutine() {
        assertError(request(
                        RoutineCategory.SKIN,
                        RepeatType.DAILY,
                        null,
                        List.of(DayOfWeek.MON)
                ),
                ErrorCode.INVALID_REPEAT_DAYS);
    }

    @Test
    void rejectsInvalidOnceDateRules() {
        assertError(request(RoutineCategory.TO_DO, RepeatType.ONCE, null, List.of()),
                ErrorCode.INVALID_ONCE_DATE);
        assertError(request(RoutineCategory.TO_DO, RepeatType.ONCE, TODAY.minusDays(1), List.of()),
                ErrorCode.INVALID_ONCE_DATE);
        assertError(request(RoutineCategory.SKIN, RepeatType.DAILY, TODAY, List.of()),
                ErrorCode.INVALID_ONCE_DATE);
    }

    @Test
    void rejectsInvalidTimeRange() {
        CreateRoutineRequest request = new CreateRoutineRequest(
                RoutineCategory.SKIN,
                "선크림 바르기",
                null,
                LocalTime.of(9, 0),
                LocalTime.of(9, 0),
                RepeatType.DAILY,
                List.of(),
                "CUP"
        );

        assertError(request, ErrorCode.INVALID_TIME_RANGE);

        CreateRoutineRequest secondPrecisionRequest = new CreateRoutineRequest(
                RoutineCategory.SKIN,
                "선크림 바르기",
                null,
                LocalTime.of(9, 0, 1),
                LocalTime.of(10, 0),
                RepeatType.DAILY,
                List.of(),
                "CUP"
        );
        assertError(secondPrecisionRequest, ErrorCode.INVALID_TIME_RANGE);
    }

    @Test
    void validatesContentAfterTrim() {
        String validContent = "가".repeat(100);
        RoutinePolicyValidator.ValidatedRoutine result = validator.validate(new CreateRoutineRequest(
                RoutineCategory.SKIN,
                "  " + validContent + "  ",
                null,
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                RepeatType.DAILY,
                List.of(),
                "CUP"
        ), TODAY);

        assertThat(result.content()).isEqualTo(validContent);
        assertThatThrownBy(() -> validator.validate(new CreateRoutineRequest(
                RoutineCategory.SKIN,
                "가".repeat(101),
                null,
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                RepeatType.DAILY,
                List.of(),
                "CUP"
        ), TODAY)).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
            assertThat(exception.getDetails()).singleElement()
                    .satisfies(detail -> assertThat(detail.field()).isEqualTo("content"));
        });
    }

    @Test
    void rejectsUnsupportedVerificationObject() {
        assertError(new CreateRoutineRequest(
                RoutineCategory.SKIN,
                "선크림 바르기",
                null,
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                RepeatType.DAILY,
                List.of(),
                "UNKNOWN"
        ), ErrorCode.VERIFICATION_OBJECT_NOT_SUPPORTED);
    }

    private CreateRoutineRequest request(
            RoutineCategory category,
            RepeatType repeatType,
            LocalDate scheduledDate,
            List<DayOfWeek> daysOfWeek
    ) {
        return new CreateRoutineRequest(
                category,
                "루틴",
                scheduledDate,
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                repeatType,
                daysOfWeek,
                "CUP"
        );
    }

    private void assertError(CreateRoutineRequest request, ErrorCode errorCode) {
        assertThatThrownBy(() -> validator.validate(request, TODAY))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }
}
