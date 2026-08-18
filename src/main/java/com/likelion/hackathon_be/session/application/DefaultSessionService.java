package com.likelion.hackathon_be.session.application;

import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.session.domain.GuestSession;
import com.likelion.hackathon_be.session.dto.CreateSessionResponse;
import com.likelion.hackathon_be.session.dto.SessionUserResponse;
import com.likelion.hackathon_be.session.repository.GuestSessionRepository;
import com.likelion.hackathon_be.user.domain.User;
import com.likelion.hackathon_be.user.repository.UserRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultSessionService implements SessionService {

    private static final String NEXT_STEP_NICKNAME_SETUP = "NICKNAME_SETUP";

    private final UserRepository userRepository;
    private final GuestSessionRepository guestSessionRepository;
    private final GuestTokenGenerator tokenGenerator;
    private final GuestTokenHasher tokenHasher;
    private final TimeProvider timeProvider;

    public DefaultSessionService(
            UserRepository userRepository,
            GuestSessionRepository guestSessionRepository,
            GuestTokenGenerator tokenGenerator,
            GuestTokenHasher tokenHasher,
            TimeProvider timeProvider
    ) {
        this.userRepository = userRepository;
        this.guestSessionRepository = guestSessionRepository;
        this.tokenGenerator = tokenGenerator;
        this.tokenHasher = tokenHasher;
        this.timeProvider = timeProvider;
    }

    @Override
    @Transactional
    public CreateSessionResponse createSession() {
        Instant now = timeProvider.now();
        User user = userRepository.save(User.createGuest(now));

        String rawToken = tokenGenerator.generate();
        String tokenHash = tokenHasher.hash(rawToken);
        GuestSession guestSession = GuestSession.create(user.getId(), tokenHash, null, now);
        guestSessionRepository.save(guestSession);

        return new CreateSessionResponse(
                rawToken,
                toOffsetDateTime(guestSession.getExpiresAt()),
                new SessionUserResponse(user.getId(), user.getNickname()),
                NEXT_STEP_NICKNAME_SETUP
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.atZone(timeProvider.serviceZone()).toOffsetDateTime();
    }
}
