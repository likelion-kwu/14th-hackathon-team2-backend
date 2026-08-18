package com.likelion.hackathon_be.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(
        @NotBlank
        String nickname
) {
}
