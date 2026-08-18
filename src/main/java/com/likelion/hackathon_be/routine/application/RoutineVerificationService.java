package com.likelion.hackathon_be.routine.application;

import com.likelion.hackathon_be.routine.dto.RoutineVerificationResultResponse;
import org.springframework.web.multipart.MultipartFile;

public interface RoutineVerificationService {

    RoutineVerificationResultResponse verifyPhoto(Long dailyRoutineId, MultipartFile photo);

    RoutineVerificationResultResponse verifyCheck(Long dailyRoutineId);
}
