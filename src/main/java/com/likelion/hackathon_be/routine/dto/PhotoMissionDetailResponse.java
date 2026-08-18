package com.likelion.hackathon_be.routine.dto;

public record PhotoMissionDetailResponse(
        Long templateId,
        String gestureCode,
        String instruction
) {
}
