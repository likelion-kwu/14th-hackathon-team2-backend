package com.likelion.hackathon_be.routine.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RoutineRepeatDayId implements Serializable {

    @Column(name = "routine_id", nullable = false)
    private Long routineId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 3)
    private DayOfWeek dayOfWeek;

    protected RoutineRepeatDayId() {
    }

    public Long getRoutineId() {
        return routineId;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RoutineRepeatDayId that)) {
            return false;
        }
        return Objects.equals(routineId, that.routineId) && dayOfWeek == that.dayOfWeek;
    }

    @Override
    public int hashCode() {
        return Objects.hash(routineId, dayOfWeek);
    }
}
