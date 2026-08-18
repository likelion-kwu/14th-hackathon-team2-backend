package com.likelion.hackathon_be.routine.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "routine_repeat_days")
public class RoutineRepeatDay {

    @EmbeddedId
    private RoutineRepeatDayId id;

    protected RoutineRepeatDay() {
    }

    public static RoutineRepeatDay of(Long routineId, DayOfWeek dayOfWeek) {
        RoutineRepeatDay repeatDay = new RoutineRepeatDay();
        repeatDay.id = RoutineRepeatDayId.of(routineId, dayOfWeek);
        return repeatDay;
    }

    public RoutineRepeatDayId getId() {
        return id;
    }
}
