package com.likelion.hackathon_be.speech.infrastructure;

import java.util.List;

public record KakaoChatData(List<KakaoParticipant> participants, List<KakaoMessage> messages) {
    public KakaoChatData {
        participants = List.copyOf(participants);
        messages = List.copyOf(messages);
    }
}
