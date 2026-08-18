package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.routine.dto.PhotoMissionResponse;

public interface PhotoMissionService {

    PhotoMissionResponse preparePhotoMission(Long dailyRoutineId);
}
