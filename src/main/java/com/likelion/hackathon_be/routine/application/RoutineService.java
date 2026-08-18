package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.routine.dto.CreateRoutineRequest;
import com.likelion.hackathon_be.routine.dto.RoutineResponse;
import com.likelion.hackathon_be.routine.dto.UpdateRoutineRequest;
import java.util.List;

public interface RoutineService {

    List<RoutineResponse> getRoutines();

    RoutineResponse getRoutine(Long routineId);

    RoutineResponse createRoutine(CreateRoutineRequest request);

    RoutineResponse updateRoutine(Long routineId, UpdateRoutineRequest request);

    void deleteRoutine(Long routineId);
}
