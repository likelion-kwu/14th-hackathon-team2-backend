package com.likelion.hackathon_be.session.application;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class GuestTokenGenerator {

    private static final String PREFIX = "guest_";
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom;

    public GuestTokenGenerator() {
        this(new SecureRandom());
    }

    GuestTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
