package com.likelion.hackathon_be.speech.application;

import com.likelion.hackathon_be.speech.domain.DialogueSituation;

public record DialogueCandidate(
        DialogueSituation situation,
        String content,
        boolean containsUserName,
        boolean containsProfanity
) {
}
