package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.routine.dto.CreateRoutineRequest;
import com.likelion.hackathon_be.routine.dto.RoutineResponse;
import com.likelion.hackathon_be.routine.dto.UpdateRoutineRequest;
import java.util.List;

public class NotImplementedRoutineService implements RoutineService {

    @Override
    public List<RoutineResponse> getRoutines() {
        throw new FeatureNotImplementedException("Routine");
    }

    @Override
    public RoutineResponse getRoutine(Long routineId) {
        throw new FeatureNotImplementedException("Routine");
    }

    @Override
    public RoutineResponse createRoutine(CreateRoutineRequest request) {
        throw new FeatureNotImplementedException("Routine");
    }

    @Override
    public RoutineResponse updateRoutine(Long routineId, UpdateRoutineRequest request) {
        throw new FeatureNotImplementedException("Routine");
    }

    @Override
    public void deleteRoutine(Long routineId) {
        throw new FeatureNotImplementedException("Routine");
    }
}
