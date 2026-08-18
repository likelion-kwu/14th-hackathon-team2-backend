package com.likelion.hackathon_be.speech.api;

import com.likelion.hackathon_be.common.api.ApiResponse;
import com.likelion.hackathon_be.speech.application.SpeechStyleService;
import com.likelion.hackathon_be.speech.dto.ApplySpeechPresetRequest;
import com.likelion.hackathon_be.speech.dto.ApplySpeechPresetResponse;
import com.likelion.hackathon_be.speech.dto.SpeechPresetResponse;
import com.likelion.hackathon_be.speech.dto.SpeechStyleResponse;
import com.likelion.hackathon_be.speech.dto.UpdateSpeechStyleRequest;
import com.likelion.hackathon_be.speech.dto.UpdateSpeechStyleResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/speech-style")
public class SpeechStyleController {

    private final SpeechStyleService speechStyleService;

    public SpeechStyleController(SpeechStyleService speechStyleService) {
        this.speechStyleService = speechStyleService;
    }

    @GetMapping("/presets")
    public ApiResponse<List<SpeechPresetResponse>> getPresets() {
        return ApiResponse.of(speechStyleService.getPresets());
    }

    @GetMapping
    public ApiResponse<SpeechStyleResponse> getCurrentStyle() {
        return ApiResponse.of(speechStyleService.getCurrentStyle());
    }

    @PostMapping("/preset")
    public ApiResponse<ApplySpeechPresetResponse> applyPreset(
            @Valid @RequestBody ApplySpeechPresetRequest request
    ) {
        return ApiResponse.of(speechStyleService.applyPreset(request));
    }

    @PatchMapping
    public ApiResponse<UpdateSpeechStyleResponse> updateStyle(
            @Valid @RequestBody UpdateSpeechStyleRequest request
    ) {
        return ApiResponse.of(speechStyleService.updateStyle(request));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetStyle() {
        speechStyleService.resetStyle();
    }
}
