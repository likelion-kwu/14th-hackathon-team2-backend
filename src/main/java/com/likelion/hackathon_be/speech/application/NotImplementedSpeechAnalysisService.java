package com.likelion.hackathon_be.speech.application;

import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.speech.dto.CreateSpeechAnalysisJobResponse;
import com.likelion.hackathon_be.speech.dto.SpeechAnalysisJobResponse;
import com.likelion.hackathon_be.speech.dto.StartSpeechAnalysisRequest;
import com.likelion.hackathon_be.speech.dto.StartSpeechAnalysisResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class NotImplementedSpeechAnalysisService implements SpeechAnalysisService {

    @Override
    public CreateSpeechAnalysisJobResponse createJob(MultipartFile file) {
        throw new FeatureNotImplementedException("Speech analysis job");
    }

    @Override
    public StartSpeechAnalysisResponse startAnalysis(UUID jobId, StartSpeechAnalysisRequest request) {
        throw new FeatureNotImplementedException("Speech analysis");
    }

    @Override
    public SpeechAnalysisJobResponse getJob(UUID jobId) {
        throw new FeatureNotImplementedException("Speech analysis job");
    }
}
