package com.likelion.hackathon_be.speech.domain;

public enum SpeechAnalysisJobStatus {
    UPLOADED,
    WAITING_PARTICIPANT_SELECTION,
    PREPROCESSING,
    ANALYZING,
    GENERATING_DIALOGUES,
    COMPLETED,
    FAILED,
    EXPIRED
}
