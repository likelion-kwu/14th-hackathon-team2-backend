package com.likelion.hackathon_be.speech.application;

import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.speech.dto.ApplySpeechPresetRequest;
import com.likelion.hackathon_be.speech.dto.ApplySpeechPresetResponse;
import com.likelion.hackathon_be.speech.dto.SpeechPresetResponse;
import com.likelion.hackathon_be.speech.dto.SpeechStyleResponse;
import com.likelion.hackathon_be.speech.dto.UpdateSpeechStyleRequest;
import com.likelion.hackathon_be.speech.dto.UpdateSpeechStyleResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NotImplementedSpeechStyleService implements SpeechStyleService {

    @Override
    public List<SpeechPresetResponse> getPresets() {
        throw new FeatureNotImplementedException("Speech preset");
    }

    @Override
    public SpeechStyleResponse getCurrentStyle() {
        throw new FeatureNotImplementedException("Speech style");
    }

    @Override
    public ApplySpeechPresetResponse applyPreset(ApplySpeechPresetRequest request) {
        throw new FeatureNotImplementedException("Speech preset");
    }

    @Override
    public UpdateSpeechStyleResponse updateStyle(UpdateSpeechStyleRequest request) {
        throw new FeatureNotImplementedException("Speech style");
    }

    @Override
    public void resetStyle() {
        throw new FeatureNotImplementedException("Speech style");
    }
}
