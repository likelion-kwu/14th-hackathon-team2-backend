package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.routine.dto.DailyRoutineListResponse;
import java.time.LocalDate;

public class NotImplementedDailyRoutineService implements DailyRoutineService {

    @Override
    public DailyRoutineListResponse getDailyRoutines(LocalDate date) {
        throw new FeatureNotImplementedException("DailyRoutine");
    }
}
