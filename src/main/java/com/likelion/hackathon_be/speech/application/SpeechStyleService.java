package com.likelion.hackathon_be.speech.application;

import com.likelion.hackathon_be.speech.dto.ApplySpeechPresetRequest;
import com.likelion.hackathon_be.speech.dto.ApplySpeechPresetResponse;
import com.likelion.hackathon_be.speech.dto.SpeechPresetResponse;
import com.likelion.hackathon_be.speech.dto.SpeechStyleResponse;
import com.likelion.hackathon_be.speech.dto.UpdateSpeechStyleRequest;
import com.likelion.hackathon_be.speech.dto.UpdateSpeechStyleResponse;
import java.util.List;

public interface SpeechStyleService {

    List<SpeechPresetResponse> getPresets();

    SpeechStyleResponse getCurrentStyle();

    ApplySpeechPresetResponse applyPreset(ApplySpeechPresetRequest request);

    UpdateSpeechStyleResponse updateStyle(UpdateSpeechStyleRequest request);

    void resetStyle();
}
