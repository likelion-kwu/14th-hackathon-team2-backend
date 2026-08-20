package com.likelion.hackathon_be.speech.infrastructure;

public record KakaoMessage(long sentAtEpochMilli, long sequence, String sender, String content) {
}
