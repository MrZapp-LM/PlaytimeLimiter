package net.playtimelimiter;

import java.time.LocalDate;

/** One tracked player: their limit setting and how much of today they have used. */
final class Entry {
    /** limitMinutes value: no individual limit, the config's default-limit applies. */
    static final int DEFAULT = -1;
    /** limitMinutes value: exempt from every limit. */
    static final int UNLIMITED = 0;

    String name;
    int limitMinutes;
    long usedSeconds;
    LocalDate day;

    Entry(String name, int limitMinutes, long usedSeconds, LocalDate day) {
        this.name = name;
        this.limitMinutes = limitMinutes;
        this.usedSeconds = usedSeconds;
        this.day = day;
    }

    /** Starts a fresh day's count once the reset time has passed. */
    synchronized void rollDay(Settings settings) {
        LocalDate today = settings.today();
        if (!today.equals(day)) {
            day = today;
            usedSeconds = 0;
        }
    }

    synchronized boolean isDefault() {
        return limitMinutes == DEFAULT;
    }

    /** The limit in minutes that applies right now: individual, default, or 0 for none. */
    synchronized int effectiveMinutes(Settings settings) {
        return limitMinutes == DEFAULT ? settings.defaultLimit() : limitMinutes;
    }

    synchronized boolean limited(Settings settings) {
        return effectiveMinutes(settings) > 0;
    }

    /** Today's limit, including any weekend bonus. Only meaningful when {@link #limited}. */
    synchronized long limitSeconds(Settings settings) {
        rollDay(settings);
        return settings.limitSeconds(effectiveMinutes(settings), day);
    }

    synchronized long remainingSeconds(Settings settings) {
        return limitSeconds(settings) - usedSeconds;
    }

    synchronized long tick(Settings settings) {
        rollDay(settings);
        usedSeconds++;
        return settings.limitSeconds(effectiveMinutes(settings), day) - usedSeconds;
    }
}
