package com.likelion.hackathon_be.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public interface TimeProvider {

    Instant now();

    LocalDate todayServiceDate();

    ZoneId serviceZone();
}
