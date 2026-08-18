package com.likelion.hackathon_be.session;

import com.likelion.hackathon_be.common.auth.BearerTokenResolver;
import com.likelion.hackathon_be.common.auth.CurrentUser;
import com.likelion.hackathon_be.common.auth.GuestSessionCurrentUserProvider;
import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import com.likelion.hackathon_be.common.time.TimeProvider;
import com.likelion.hackathon_be.session.application.DefaultSessionService;
import com.likelion.hackathon_be.session.application.GuestTokenGenerator;
import com.likelion.hackathon_be.session.application.GuestTokenHasher;
import com.likelion.hackathon_be.session.domain.GuestSession;
import com.likelion.hackathon_be.session.dto.CreateSessionResponse;
import com.likelion.hackathon_be.session.repository.GuestSessionRepository;
import com.likelion.hackathon_be.user.domain.User;
import com.likelion.hackathon_be.user.repository.UserRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionAuthTests {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    private final GuestTokenHasher tokenHasher = new GuestTokenHasher();
    private final TimeProvider timeProvider = new FixedTimeProvider(NOW);

    @Test
    void createSessionCreatesUserAndGuestSessionWithHashedTokenOnly() {
        UserRepository userRepository = mock(UserRepository.class);
        GuestSessionRepository guestSessionRepository = mock(GuestSessionRepository.class);
        DefaultSessionService service = new DefaultSessionService(
                userRepository,
                guestSessionRepository,
                new GuestTokenGenerator(),
                tokenHasher,
                timeProvider
        );

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            setField(user, "id", 1001L);
            return user;
        });
        when(guestSessionRepository.save(any(GuestSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateSessionResponse response = service.createSession();

        ArgumentCaptor<GuestSession> sessionCaptor = ArgumentCaptor.forClass(GuestSession.class);
        verify(guestSessionRepository).save(sessionCaptor.capture());
        GuestSession savedSession = sessionCaptor.getValue();

        assertThat(response.accessToken()).startsWith("guest_");
        assertThat(response.expiresAt()).isNull();
        assertThat(response.user().id()).isEqualTo(1001L);
        assertThat(response.user().nickname()).isNull();
        assertThat(response.nextStep()).isEqualTo("NICKNAME_SETUP");
        assertThat(savedSession.getUserId()).isEqualTo(1001L);
        assertThat(savedSession.getTokenHash()).isEqualTo(tokenHasher.hash(response.accessToken()));
        assertThat(savedSession.getTokenHash()).isNotEqualTo(response.accessToken());
        assertThat(savedSession.getExpiresAt()).isNull();
    }

    @Test
    void tokenGeneratorCreatesNonBlankDifferentTokens() {
        GuestTokenGenerator generator = new GuestTokenGenerator();

        String first = generator.generate();
        String second = generator.generate();

        assertThat(first).startsWith("guest_");
        assertThat(second).startsWith("guest_");
        assertThat(first).isNotBlank();
        assertThat(second).isNotBlank();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void validBearerTokenReturnsCurrentUser() {
        String rawToken = "guest_valid";
        String tokenHash = tokenHasher.hash(rawToken);
        GuestSessionRepository guestSessionRepository = mock(GuestSessionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        User user = userWithId(10L);
        GuestSession guestSession = GuestSession.create(10L, tokenHash, null, NOW);
        GuestSessionCurrentUserProvider provider = new GuestSessionCurrentUserProvider(
                resolverReturning(rawToken),
                tokenHasher,
                guestSessionRepository,
                userRepository,
                timeProvider
        );

        when(guestSessionRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(guestSession));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        CurrentUser currentUser = provider.getCurrentUser();

        assertThat(currentUser.id()).isEqualTo(10L);
    }

    @Test
    void missingAuthorizationHeaderThrows401() {
        BearerTokenResolver resolver = new BearerTokenResolver(new MockHttpServletRequest());

        assertUnauthorized(resolver::resolve);
    }

    @Test
    void malformedBearerHeaderThrows401() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Basic guest_invalid");
        BearerTokenResolver resolver = new BearerTokenResolver(request);

        assertUnauthorized(resolver::resolve);
    }

    @Test
    void blankBearerTokenThrows401() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer ");
        BearerTokenResolver resolver = new BearerTokenResolver(request);

        assertUnauthorized(resolver::resolve);
    }

    @Test
    void unknownTokenThrows401() {
        GuestSessionRepository guestSessionRepository = mock(GuestSessionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GuestSessionCurrentUserProvider provider = new GuestSessionCurrentUserProvider(
                resolverReturning("guest_unknown"),
                tokenHasher,
                guestSessionRepository,
                userRepository,
                timeProvider
        );

        when(guestSessionRepository.findByTokenHash(tokenHasher.hash("guest_unknown"))).thenReturn(Optional.empty());

        assertUnauthorized(provider::getCurrentUser);
    }

    @Test
    void expiredSessionThrows401() {
        String rawToken = "guest_expired";
        String tokenHash = tokenHasher.hash(rawToken);
        GuestSessionRepository guestSessionRepository = mock(GuestSessionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GuestSession expiredSession = GuestSession.create(10L, tokenHash, NOW, NOW);
        GuestSessionCurrentUserProvider provider = new GuestSessionCurrentUserProvider(
                resolverReturning(rawToken),
                tokenHasher,
                guestSessionRepository,
                userRepository,
                timeProvider
        );

        when(guestSessionRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expiredSession));

        assertUnauthorized(provider::getCurrentUser);
    }

    @Test
    void nullExpiresAtSessionIsValid() {
        String rawToken = "guest_without_expiry";
        String tokenHash = tokenHasher.hash(rawToken);
        GuestSessionRepository guestSessionRepository = mock(GuestSessionRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        GuestSession guestSession = GuestSession.create(20L, tokenHash, null, NOW);
        GuestSessionCurrentUserProvider provider = new GuestSessionCurrentUserProvider(
                resolverReturning(rawToken),
                tokenHasher,
                guestSessionRepository,
                userRepository,
                timeProvider
        );

        when(guestSessionRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(guestSession));
        when(userRepository.findById(20L)).thenReturn(Optional.of(userWithId(20L)));

        assertThat(provider.getCurrentUser().id()).isEqualTo(20L);
    }

    private BearerTokenResolver resolverReturning(String token) {
        BearerTokenResolver resolver = mock(BearerTokenResolver.class);
        when(resolver.resolve()).thenReturn(token);
        return resolver;
    }

    private User userWithId(Long id) {
        User user = User.createGuest(NOW);
        setField(user, "id", id);
        return user;
    }

    private void assertUnauthorized(ThrowingRunnable runnable) {
        assertThatThrownBy(runnable::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
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

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
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
