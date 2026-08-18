package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.routine.dto.PointClaimResponse;

public interface RoutinePointService {

    PointClaimResponse claimPoint(Long dailyRoutineId);
}
