package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.routine.dto.RoutineVerificationResultResponse;
import com.likelion.hackathon_be.routine.verification.domain.VerificationType;
import java.time.Instant;

public interface RoutineCompletionService {

    RoutineVerificationResultResponse complete(
            Long userId,
            Long dailyRoutineId,
            VerificationType verificationType,
            Instant verificationRequestedAt
    );
}
