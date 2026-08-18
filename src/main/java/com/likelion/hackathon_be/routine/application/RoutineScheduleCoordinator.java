package com.likelion.hackathon_be.routine.application;

import java.time.LocalDate;

public interface RoutineScheduleCoordinator {

    boolean isServiceDateLocked(Long userId, LocalDate serviceDate);

    void preserveHistory(Long routineId, LocalDate throughDate);

    void synchronizeUpdatedRoutine(Long routineId, Long userId, LocalDate resynchronizeFrom);

    void synchronizeDeletedRoutine(Long routineId, Long userId, LocalDate deleteFrom);
}
