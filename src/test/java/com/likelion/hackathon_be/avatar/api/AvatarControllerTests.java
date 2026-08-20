package com.likelion.hackathon_be.avatar.api;

import com.likelion.hackathon_be.avatar.application.AvatarService;
import com.likelion.hackathon_be.avatar.domain.AvatarGrowthTrack;
import com.likelion.hackathon_be.avatar.dto.CreateAvatarResponse;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AvatarControllerTests {

    private final AvatarService avatarService = mock(AvatarService.class);
    private final AvatarController controller = new AvatarController(avatarService);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

    @Test
    void createsAvatarFromMultipartGrowthTrackAndOptionalFacePhoto() throws Exception {
        MockMultipartFile facePhoto = new MockMultipartFile(
                "facePhoto",
                "face.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{1, 2, 3}
        );
        when(avatarService.createAvatar(AvatarGrowthTrack.SKIN, facePhoto)).thenReturn(new CreateAvatarResponse(
                12L,
                true,
                "SKIN",
                1,
                "/api/v1/avatars/me/image",
                "GENERATED",
                false,
                1,
                "SPEECH_STYLE_SETUP"
        ));

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/v1/avatars/me")
                        .file(facePhoto)
                        .param("growthTrack", "SKIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(12))
                .andExpect(jsonPath("$.data.growthTrack").value("SKIN"))
                .andExpect(jsonPath("$.data.imageEndpoint").value("/api/v1/avatars/me/image"))
                .andExpect(jsonPath("$.data.fallbackUsed").value(false));

        verify(avatarService).createAvatar(eq(AvatarGrowthTrack.SKIN), same(facePhoto));
    }

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
