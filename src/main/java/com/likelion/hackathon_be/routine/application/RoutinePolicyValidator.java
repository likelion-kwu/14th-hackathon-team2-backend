package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.error.ValidationErrorDetail;
import com.likelion.hackathon_be.routine.domain.DayOfWeek;
import com.likelion.hackathon_be.routine.domain.RepeatType;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import com.likelion.hackathon_be.routine.dto.CreateRoutineRequest;
import com.likelion.hackathon_be.routine.dto.UpdateRoutineRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RoutinePolicyValidator {

    private static final int MAX_CONTENT_LENGTH = 100;
    private static final int MAX_VERIFICATION_OBJECT_LENGTH = 40;

    private final RoutineCatalogService routineCatalogService;

    public RoutinePolicyValidator(RoutineCatalogService routineCatalogService) {
        this.routineCatalogService = routineCatalogService;
    }

    public ValidatedRoutine validate(CreateRoutineRequest request, LocalDate today) {
        if (request == null) {
            throw validationError("request", "요청 본문이 필요합니다.");
        }
        return validate(
                request.category(),
                request.content(),
                request.scheduledDate(),
                request.startTime(),
                request.endTime(),
                request.repeatType(),
                request.daysOfWeek(),
                request.verificationObject(),
                today
        );
    }

    public ValidatedRoutine validate(UpdateRoutineRequest request, LocalDate today) {
        if (request == null) {
            throw validationError("request", "요청 본문이 필요합니다.");
        }
        return validate(
                request.category(),
                request.content(),
                request.scheduledDate(),
                request.startTime(),
                request.endTime(),
                request.repeatType(),
                request.daysOfWeek(),
                request.verificationObject(),
                today
        );
    }

    private ValidatedRoutine validate(
            RoutineCategory category,
            String content,
            LocalDate scheduledDate,
            LocalTime startTime,
            LocalTime endTime,
            RepeatType repeatType,
            List<DayOfWeek> daysOfWeek,
            String verificationObject,
            LocalDate today
    ) {
        validateCategoryAndRepeatType(category, repeatType);
        String normalizedContent = normalizeContent(content);
        validateTimeRange(startTime, endTime);
        List<DayOfWeek> normalizedDays = normalizeRepeatDays(repeatType, daysOfWeek);
        validateScheduledDate(repeatType, scheduledDate, today);
        validateVerificationObject(verificationObject);

        return new ValidatedRoutine(
                category,
                normalizedContent,
                scheduledDate,
                startTime,
                endTime,
                repeatType,
                normalizedDays,
                verificationObject
        );
    }

    private void validateCategoryAndRepeatType(RoutineCategory category, RepeatType repeatType) {
        if (category == null) {
            throw new BusinessException(ErrorCode.INVALID_ROUTINE_CATEGORY);
        }
        if (repeatType == null) {
            throw new BusinessException(ErrorCode.INVALID_REPEAT_TYPE_FOR_CATEGORY);
        }

        boolean valid = category == RoutineCategory.TO_DO
                ? repeatType == RepeatType.ONCE
                : repeatType == RepeatType.DAILY || repeatType == RepeatType.DAYS_OF_WEEK;
        if (!valid) {
            throw new BusinessException(ErrorCode.INVALID_REPEAT_TYPE_FOR_CATEGORY);
        }
    }

    private String normalizeContent(String content) {
        String normalized = content == null ? "" : content.strip();
        if (normalized.isEmpty() || normalized.length() > MAX_CONTENT_LENGTH) {
            throw validationError("content", "trim 후 1~100자여야 합니다.");
        }
        return normalized;
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime == null
                || endTime == null
                || hasSubMinutePrecision(startTime)
                || hasSubMinutePrecision(endTime)
                || !startTime.isBefore(endTime)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
        }
    }

    private boolean hasSubMinutePrecision(LocalTime time) {
        return time.getSecond() != 0 || time.getNano() != 0;
    }

    private List<DayOfWeek> normalizeRepeatDays(RepeatType repeatType, List<DayOfWeek> daysOfWeek) {
        List<DayOfWeek> requestedDays = daysOfWeek == null ? List.of() : daysOfWeek;
        EnumSet<DayOfWeek> uniqueDays = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek day : requestedDays) {
            if (day == null || !uniqueDays.add(day)) {
                throw new BusinessException(ErrorCode.INVALID_REPEAT_DAYS);
            }
        }

        if (repeatType == RepeatType.DAYS_OF_WEEK && uniqueDays.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REPEAT_DAYS);
        }
        if (repeatType != RepeatType.DAYS_OF_WEEK && !uniqueDays.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REPEAT_DAYS);
        }
        return List.copyOf(new ArrayList<>(uniqueDays));
    }

    private void validateScheduledDate(RepeatType repeatType, LocalDate scheduledDate, LocalDate today) {
        if (repeatType == RepeatType.ONCE) {
            if (scheduledDate == null || scheduledDate.isBefore(today)) {
                throw new BusinessException(ErrorCode.INVALID_ONCE_DATE);
            }
            return;
        }
        if (scheduledDate != null) {
            throw new BusinessException(ErrorCode.INVALID_ONCE_DATE);
        }
    }

    private void validateVerificationObject(String verificationObject) {
        if (verificationObject == null
                || verificationObject.isBlank()
                || verificationObject.length() > MAX_VERIFICATION_OBJECT_LENGTH
                || !routineCatalogService.supportsVerificationObject(verificationObject)) {
            throw new BusinessException(ErrorCode.VERIFICATION_OBJECT_NOT_SUPPORTED);
        }
    }

    private BusinessException validationError(String field, String reason) {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR.defaultMessage(),
                List.of(new ValidationErrorDetail(field, reason))
        );
    }

    public record ValidatedRoutine(
            RoutineCategory category,
            String content,
            LocalDate scheduledDate,
            LocalTime startTime,
            LocalTime endTime,
            RepeatType repeatType,
            List<DayOfWeek> daysOfWeek,
            String verificationObject
    ) {
    }
}
