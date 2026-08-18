package com.likelion.hackathon_be.speech.api;

import com.likelion.hackathon_be.common.api.ApiResponse;
import com.likelion.hackathon_be.speech.application.SpeechAnalysisService;
import com.likelion.hackathon_be.speech.dto.CreateSpeechAnalysisJobResponse;
import com.likelion.hackathon_be.speech.dto.SpeechAnalysisJobResponse;
import com.likelion.hackathon_be.speech.dto.StartSpeechAnalysisRequest;
import com.likelion.hackathon_be.speech.dto.StartSpeechAnalysisResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/speech-style/kakao/jobs")
public class SpeechAnalysisJobController {

    private final SpeechAnalysisService speechAnalysisService;

    public SpeechAnalysisJobController(SpeechAnalysisService speechAnalysisService) {
        this.speechAnalysisService = speechAnalysisService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreateSpeechAnalysisJobResponse> createJob(
            @NotNull @RequestParam MultipartFile file
    ) {
        return ApiResponse.of(speechAnalysisService.createJob(file));
    }

    @PostMapping("/{jobId}/analyze")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<StartSpeechAnalysisResponse> startAnalysis(
            @PathVariable UUID jobId,
            @Valid @RequestBody StartSpeechAnalysisRequest request
    ) {
        return ApiResponse.of(speechAnalysisService.startAnalysis(jobId, request));
    }

    @GetMapping("/{jobId}")
    public ApiResponse<SpeechAnalysisJobResponse> getJob(@PathVariable UUID jobId) {
        return ApiResponse.of(speechAnalysisService.getJob(jobId));
    }
}
