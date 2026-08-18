package com.likelion.hackathon_be.routine.daily.application;

import java.time.LocalDate;

public interface DailyRoutineMaterializationService {

    int ensureMaterializedForUser(Long userId);

    int ensureMaterializedForRoutine(Long routineId, LocalDate throughDate);
}
