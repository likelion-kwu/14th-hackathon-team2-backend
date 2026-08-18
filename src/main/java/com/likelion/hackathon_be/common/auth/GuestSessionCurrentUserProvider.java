package com.likelion.hackathon_be.common.auth;

import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.session.application.GuestTokenHasher;
import com.likelion.hackathon_be.session.domain.GuestSession;
import com.likelion.hackathon_be.session.repository.GuestSessionRepository;
import com.likelion.hackathon_be.user.repository.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GuestSessionCurrentUserProvider implements CurrentUserProvider {

    private final BearerTokenResolver bearerTokenResolver;
    private final GuestTokenHasher tokenHasher;
    private final GuestSessionRepository guestSessionRepository;
    private final UserRepository userRepository;
    private final TimeProvider timeProvider;

    public GuestSessionCurrentUserProvider(
            BearerTokenResolver bearerTokenResolver,
            GuestTokenHasher tokenHasher,
            GuestSessionRepository guestSessionRepository,
            UserRepository userRepository,
            TimeProvider timeProvider
    ) {
        this.bearerTokenResolver = bearerTokenResolver;
        this.tokenHasher = tokenHasher;
        this.guestSessionRepository = guestSessionRepository;
        this.userRepository = userRepository;
        this.timeProvider = timeProvider;
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentUser getCurrentUser() {
        String rawToken = bearerTokenResolver.resolve();
        String tokenHash = tokenHasher.hash(rawToken);
        GuestSession guestSession = guestSessionRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        if (isExpired(guestSession, timeProvider.now())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        userRepository.findById(guestSession.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        return new CurrentUser(guestSession.getUserId());
    }

    private boolean isExpired(GuestSession guestSession, Instant now) {
        Instant expiresAt = guestSession.getExpiresAt();
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
