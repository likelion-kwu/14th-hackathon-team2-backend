package com.likelion.hackathon_be.common.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class SystemTimeProvider implements TimeProvider {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final Clock clock;

    public SystemTimeProvider() {
        this(Clock.system(SERVICE_ZONE));
    }

    SystemTimeProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Instant now() {
        return clock.instant();
    }

    @Override
    public LocalDate todayServiceDate() {
        return LocalDate.now(clock);
    }

    @Override
    public ZoneId serviceZone() {
        return SERVICE_ZONE;
    }
}
