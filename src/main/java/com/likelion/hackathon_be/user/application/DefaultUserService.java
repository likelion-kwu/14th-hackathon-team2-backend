package com.likelion.hackathon_be.user.application;

import com.likelion.hackathon_be.avatar.repository.AvatarRepository;
import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.error.ValidationErrorDetail;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.speech.repository.SpeechStyleProfileRepository;
import com.likelion.hackathon_be.user.domain.User;
import com.likelion.hackathon_be.user.dto.CurrentUserResponse;
import com.likelion.hackathon_be.user.dto.UpdateUserRequest;
import com.likelion.hackathon_be.user.dto.UpdateUserResponse;
import com.likelion.hackathon_be.user.repository.UserRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultUserService implements UserService {

    private static final int MAX_NICKNAME_LENGTH = 30;

    private static final String NICKNAME_SETUP = "NICKNAME_SETUP";
    private static final String AVATAR_SETUP = "AVATAR_SETUP";
    private static final String SPEECH_STYLE_SETUP = "SPEECH_STYLE_SETUP";
    private static final String HOME = "HOME";

    private final CurrentUserProvider currentUserProvider;
    private final UserRepository userRepository;
    private final AvatarRepository avatarRepository;
    private final SpeechStyleProfileRepository speechStyleProfileRepository;
    private final TimeProvider timeProvider;

    public DefaultUserService(
            CurrentUserProvider currentUserProvider,
            UserRepository userRepository,
            AvatarRepository avatarRepository,
            SpeechStyleProfileRepository speechStyleProfileRepository,
            TimeProvider timeProvider
    ) {
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
        this.avatarRepository = avatarRepository;
        this.speechStyleProfileRepository = speechStyleProfileRepository;
        this.timeProvider = timeProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUserResponse getMe() {
        User user = currentUser();
        boolean avatarConfigured = avatarRepository.existsByUserId(user.getId());
        boolean speechStyleConfigured = speechStyleProfileRepository.existsByUserId(user.getId());

        return new CurrentUserResponse(
                user.getId(),
                user.getNickname(),
                avatarConfigured,
                speechStyleConfigured,
                nextStep(user.getNickname(), avatarConfigured, speechStyleConfigured),
                toOffsetDateTime(user.getCreatedAt())
        );
    }

    @Override
    @Transactional
    public UpdateUserResponse updateMe(UpdateUserRequest request) {
        User user = currentUser();
        String nickname = normalizeNickname(request == null ? null : request.nickname());
        Instant now = timeProvider.now();
        user.updateNickname(nickname, now);

        boolean avatarConfigured = avatarRepository.existsByUserId(user.getId());
        boolean speechStyleConfigured = speechStyleProfileRepository.existsByUserId(user.getId());

        return new UpdateUserResponse(
                user.getId(),
                user.getNickname(),
                nextStep(user.getNickname(), avatarConfigured, speechStyleConfigured),
                toOffsetDateTime(user.getUpdatedAt())
        );
    }

    private User currentUser() {
        Long userId = currentUserProvider.getCurrentUser().id();
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    private String normalizeNickname(String nickname) {
        String normalized = nickname == null ? "" : nickname.trim();
        if (normalized.isEmpty() || normalized.length() > MAX_NICKNAME_LENGTH) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(),
                    List.of(new ValidationErrorDetail("nickname", "trim 후 1~30자여야 합니다."))
            );
        }
        return normalized;
    }

    private String nextStep(String nickname, boolean avatarConfigured, boolean speechStyleConfigured) {
        if (nickname == null || nickname.isBlank()) {
            return NICKNAME_SETUP;
        }
        if (!avatarConfigured) {
            return AVATAR_SETUP;
        }
        if (!speechStyleConfigured) {
            return SPEECH_STYLE_SETUP;
        }
        return HOME;
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atZone(timeProvider.serviceZone()).toOffsetDateTime();
    }
}
