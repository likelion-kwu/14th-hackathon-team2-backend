package com.likelion.hackathon_be.speech.dto;

import com.likelion.hackathon_be.speech.domain.SentenceLength;
import com.likelion.hackathon_be.speech.domain.SpeechAttributeLevel;
import com.likelion.hackathon_be.speech.domain.SpeechLevel;

public record UpdateSpeechStyleRequest(
        SpeechLevel speechLevel,
        SentenceLength sentenceLength,
        SpeechAttributeLevel directness,
        SpeechAttributeLevel warmth,
        SpeechAttributeLevel playfulness,
        Boolean profanityEnabled
) {
}
