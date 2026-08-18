package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.routine.dto.PhotoMissionResponse;

public class NotImplementedPhotoMissionService implements PhotoMissionService {

    @Override
    public PhotoMissionResponse preparePhotoMission(Long dailyRoutineId) {
        throw new FeatureNotImplementedException("Photo mission");
    }
}
