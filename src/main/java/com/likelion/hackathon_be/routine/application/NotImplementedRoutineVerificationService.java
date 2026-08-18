package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.routine.dto.RoutineVerificationResultResponse;
import org.springframework.web.multipart.MultipartFile;

public class NotImplementedRoutineVerificationService implements RoutineVerificationService {

    @Override
    public RoutineVerificationResultResponse verifyPhoto(Long dailyRoutineId, MultipartFile photo) {
        throw new FeatureNotImplementedException("Routine PHOTO verification");
    }

    @Override
    public RoutineVerificationResultResponse verifyCheck(Long dailyRoutineId) {
        throw new FeatureNotImplementedException("Routine CHECK verification");
    }
}
