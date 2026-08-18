package com.likelion.hackathon_be.common.error;

public record ValidationErrorDetail(
        String field,
        String reason
) {
}
