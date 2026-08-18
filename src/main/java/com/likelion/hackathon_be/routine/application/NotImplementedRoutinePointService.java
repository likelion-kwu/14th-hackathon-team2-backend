package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.routine.dto.PointClaimResponse;

public class NotImplementedRoutinePointService implements RoutinePointService {

    @Override
    public PointClaimResponse claimPoint(Long dailyRoutineId) {
        throw new FeatureNotImplementedException("Routine point claim");
    }
}
