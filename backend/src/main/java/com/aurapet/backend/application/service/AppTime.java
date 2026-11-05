package com.aurapet.backend.application.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Mọi khái niệm "hôm nay", "giờ cho ăn" đều tính theo giờ Việt Nam. */
public final class AppTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private AppTime() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static Instant startOfToday() {
        return today().atStartOfDay(ZONE).toInstant();
    }

    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }

    public static LocalDate dateOf(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }
}
