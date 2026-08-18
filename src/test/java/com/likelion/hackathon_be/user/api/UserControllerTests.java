package com.likelion.hackathon_be.user.api;

import com.likelion.hackathon_be.common.error.GlobalExceptionHandler;
import com.likelion.hackathon_be.user.application.UserService;
import com.likelion.hackathon_be.user.dto.CurrentUserResponse;
import com.likelion.hackathon_be.user.dto.UpdateUserRequest;
import com.likelion.hackathon_be.user.dto.UpdateUserResponse;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTests {

    private UserService userService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getMeReturnsApiResponseContract() throws Exception {
        when(userService.getMe()).thenReturn(new CurrentUserResponse(
                1001L,
                "김멋사",
                true,
                true,
                "HOME",
                OffsetDateTime.parse("2026-08-17T18:20:00+09:00")
        ));

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1001))
                .andExpect(jsonPath("$.data.nickname").value("김멋사"))
                .andExpect(jsonPath("$.data.avatarConfigured").value(true))
                .andExpect(jsonPath("$.data.speechStyleConfigured").value(true))
                .andExpect(jsonPath("$.data.nextStep").value("HOME"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-08-17T18:20:00+09:00"));
    }

    @Test
    void updateMeReturnsApiResponseContract() throws Exception {
        when(userService.updateMe(any())).thenReturn(new UpdateUserResponse(
                1001L,
                "김멋사",
                "AVATAR_SETUP",
                OffsetDateTime.parse("2026-08-17T18:22:00+09:00")
        ));

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"  김멋사  "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1001))
                .andExpect(jsonPath("$.data.nickname").value("김멋사"))
                .andExpect(jsonPath("$.data.nextStep").value("AVATAR_SETUP"))
                .andExpect(jsonPath("$.data.updatedAt").value("2026-08-17T18:22:00+09:00"));

        ArgumentCaptor<UpdateUserRequest> captor = ArgumentCaptor.forClass(UpdateUserRequest.class);
        verify(userService).updateMe(captor.capture());
        assertThat(captor.getValue().nickname()).isEqualTo("  김멋사  ");
    }

    @Test
    void updateMeRejectsBlankNicknameBeforeServiceCall() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value("nickname"));

        verify(userService, never()).updateMe(any());
    }
}
