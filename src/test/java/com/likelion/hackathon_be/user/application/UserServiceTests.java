package com.likelion.hackathon_be.user.application;

import com.likelion.hackathon_be.avatar.repository.AvatarRepository;
import com.likelion.hackathon_be.common.auth.CurrentUser;
import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.speech.repository.SpeechStyleProfileRepository;
import com.likelion.hackathon_be.user.domain.User;
import com.likelion.hackathon_be.user.dto.CurrentUserResponse;
import com.likelion.hackathon_be.user.dto.UpdateUserRequest;
import com.likelion.hackathon_be.user.dto.UpdateUserResponse;
import com.likelion.hackathon_be.user.repository.UserRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTests {

    private static final Long USER_ID = 1001L;
    private static final Instant CREATED_AT = Instant.parse("2026-08-17T09:20:00Z");
    private static final Instant NOW = Instant.parse("2026-08-19T09:22:00Z");

    private CurrentUserProvider currentUserProvider;
    private UserRepository userRepository;
    private AvatarRepository avatarRepository;
    private SpeechStyleProfileRepository speechStyleProfileRepository;
    private DefaultUserService service;
    private User user;

    @BeforeEach
    void setUp() {
        currentUserProvider = mock(CurrentUserProvider.class);
        userRepository = mock(UserRepository.class);
        avatarRepository = mock(AvatarRepository.class);
        speechStyleProfileRepository = mock(SpeechStyleProfileRepository.class);
        service = new DefaultUserService(
                currentUserProvider,
                userRepository,
                avatarRepository,
                speechStyleProfileRepository,
                new FixedTimeProvider(NOW)
        );

        user = user(USER_ID, "김멋사");
        when(currentUserProvider.getCurrentUser()).thenReturn(new CurrentUser(USER_ID));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @Test
    void getMeReturnsConfigurationAndHomeStep() {
        when(avatarRepository.existsByUserId(USER_ID)).thenReturn(true);
        when(speechStyleProfileRepository.existsByUserId(USER_ID)).thenReturn(true);

        CurrentUserResponse response = service.getMe();

        assertThat(response.id()).isEqualTo(USER_ID);
        assertThat(response.nickname()).isEqualTo("김멋사");
        assertThat(response.avatarConfigured()).isTrue();
        assertThat(response.speechStyleConfigured()).isTrue();
        assertThat(response.nextStep()).isEqualTo("HOME");
        assertThat(response.createdAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(response.createdAt().toInstant()).isEqualTo(CREATED_AT);
    }

    @Test
    void getMeRequiresNicknameBeforeOtherOnboardingSteps() {
        user = user(USER_ID, null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(avatarRepository.existsByUserId(USER_ID)).thenReturn(true);
        when(speechStyleProfileRepository.existsByUserId(USER_ID)).thenReturn(true);

        assertThat(service.getMe().nextStep()).isEqualTo("NICKNAME_SETUP");
    }

    @Test
    void getMeRequiresAvatarBeforeSpeechStyle() {
        when(avatarRepository.existsByUserId(USER_ID)).thenReturn(false);
        when(speechStyleProfileRepository.existsByUserId(USER_ID)).thenReturn(true);

        assertThat(service.getMe().nextStep()).isEqualTo("AVATAR_SETUP");
    }

    @Test
    void getMeRequiresSpeechStyleAfterAvatar() {
        when(avatarRepository.existsByUserId(USER_ID)).thenReturn(true);
        when(speechStyleProfileRepository.existsByUserId(USER_ID)).thenReturn(false);

        assertThat(service.getMe().nextStep()).isEqualTo("SPEECH_STYLE_SETUP");
    }

    @Test
    void updateMeTrimsNicknameAndUpdatesTimestamp() {
        when(avatarRepository.existsByUserId(USER_ID)).thenReturn(false);
        when(speechStyleProfileRepository.existsByUserId(USER_ID)).thenReturn(false);

        UpdateUserResponse response = service.updateMe(new UpdateUserRequest("  새 닉네임  "));

        assertThat(user.getNickname()).isEqualTo("새 닉네임");
        assertThat(user.getUpdatedAt()).isEqualTo(NOW);
        assertThat(response.nickname()).isEqualTo("새 닉네임");
        assertThat(response.nextStep()).isEqualTo("AVATAR_SETUP");
        assertThat(response.updatedAt().toInstant()).isEqualTo(NOW);
        assertThat(response.updatedAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    }

    @Test
    void updateMeAcceptsThirtyCharactersAfterTrim() {
        String nickname = "가".repeat(30);

        UpdateUserResponse response = service.updateMe(new UpdateUserRequest("  " + nickname + "  "));

        assertThat(response.nickname()).isEqualTo(nickname);
    }

    @Test
    void updateMeRejectsBlankNicknameAfterTrim() {
        assertNicknameValidationError("   ");
    }

    @Test
    void updateMeRejectsNicknameLongerThanThirtyCharactersAfterTrim() {
        assertNicknameValidationError("가".repeat(31));
    }

    @Test
    void missingAuthenticatedUserIsUnauthorized() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(service::getMe)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    private void assertNicknameValidationError(String nickname) {
        assertThatThrownBy(() -> service.updateMe(new UpdateUserRequest(nickname)))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getDetails())
                            .extracting(detail -> detail.field(), detail -> detail.reason())
                            .containsExactly(tuple("nickname", "trim 후 1~30자여야 합니다."));
                });
    }

    private User user(Long id, String nickname) {
        User value = User.createGuest(CREATED_AT);
        setField(value, "id", id);
        if (nickname != null) {
            value.updateNickname(nickname, CREATED_AT);
        }
        return value;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static org.assertj.core.groups.Tuple tuple(Object... values) {
        return org.assertj.core.groups.Tuple.tuple(values);
    }

    private record FixedTimeProvider(Instant now) implements TimeProvider {

        @Override
        public LocalDate todayServiceDate() {
            return LocalDate.ofInstant(now, serviceZone());
        }

        @Override
        public ZoneId serviceZone() {
            return ZoneId.of("Asia/Seoul");
        }
    }
}
