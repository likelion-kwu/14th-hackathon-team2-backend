package com.likelion.hackathon_be.avatar.api;

import com.likelion.hackathon_be.avatar.application.AvatarService;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AvatarControllerTests {

    private final AvatarService avatarService = mock(AvatarService.class);
    private final AvatarController controller = new AvatarController(avatarService);

    @Test
    void missingOrBlankGrowthTrackIsReportedAsRequired() {
        assertGrowthTrackError(null, ErrorCode.AVATAR_TRACK_REQUIRED);
        assertGrowthTrackError("", ErrorCode.AVATAR_TRACK_REQUIRED);
        assertGrowthTrackError("   ", ErrorCode.AVATAR_TRACK_REQUIRED);
        verifyNoInteractions(avatarService);
    }

    @Test
    void unknownOrRoutineCategoryGrowthTrackIsReportedAsValidationError() {
        assertGrowthTrackError("UNKNOWN", ErrorCode.VALIDATION_ERROR);
        assertGrowthTrackError("TO_DO", ErrorCode.VALIDATION_ERROR);
        verifyNoInteractions(avatarService);
    }

    private void assertGrowthTrackError(String growthTrack, ErrorCode expected) {
        assertThatThrownBy(() -> controller.createAvatar(growthTrack, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
