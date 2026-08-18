package com.likelion.hackathon_be.home.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record HomeResponse(
        LocalDate serviceDate,
        OffsetDateTime serverNow,
        HomeAvatarResponse avatar,
        HomeProgressResponse progress,
        HomePointsResponse points,
        HomeSuccessResponse success,
        HomeUnlockProgressResponse unlockProgress,
        List<HomeRoutineResponse> routines
) {
}
