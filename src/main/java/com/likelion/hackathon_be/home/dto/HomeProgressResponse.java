package com.likelion.hackathon_be.home.dto;

public record HomeProgressResponse(
        int completedCount,
        int totalCount,
        int percentage,
        String dayStatus
) {
}
