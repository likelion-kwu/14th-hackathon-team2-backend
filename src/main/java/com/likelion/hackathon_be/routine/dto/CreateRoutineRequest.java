package com.likelion.hackathon_be.routine.dto;

import com.likelion.hackathon_be.routine.domain.DayOfWeek;
import com.likelion.hackathon_be.routine.domain.RepeatType;
import com.likelion.hackathon_be.routine.domain.RoutineCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record CreateRoutineRequest(
        @NotNull
        RoutineCategory category,
        @NotBlank
        String content,
        LocalDate scheduledDate,
        @NotNull
        LocalTime startTime,
        @NotNull
        LocalTime endTime,
        @NotNull
        RepeatType repeatType,
        List<@NotNull DayOfWeek> daysOfWeek,
        @NotBlank
        @Size(max = 40)
        String verificationObject
) {
}
