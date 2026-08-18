package com.likelion.hackathon_be.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank
        @Size(max = 30)
        String nickname
) {
}
