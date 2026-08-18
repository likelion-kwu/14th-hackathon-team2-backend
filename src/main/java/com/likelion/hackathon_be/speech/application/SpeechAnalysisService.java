package com.likelion.hackathon_be.speech.application;

import com.likelion.hackathon_be.speech.dto.CreateSpeechAnalysisJobResponse;
import com.likelion.hackathon_be.speech.dto.SpeechAnalysisJobResponse;
import com.likelion.hackathon_be.speech.dto.StartSpeechAnalysisRequest;
import com.likelion.hackathon_be.speech.dto.StartSpeechAnalysisResponse;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface SpeechAnalysisService {

    CreateSpeechAnalysisJobResponse createJob(MultipartFile file);

    StartSpeechAnalysisResponse startAnalysis(UUID jobId, StartSpeechAnalysisRequest request);

    SpeechAnalysisJobResponse getJob(UUID jobId);
}
