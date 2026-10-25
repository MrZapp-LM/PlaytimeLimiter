package net.playtimelimiter;

import java.time.LocalDate;

/** One limited player: their daily limit and how much of today they have used. */
final class Entry {
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

    /** Today's limit, including any weekend bonus. */
    synchronized long limitSeconds(Settings settings) {
        rollDay(settings);
        return settings.limitSeconds(limitMinutes, day);
    }

    synchronized long remainingSeconds(Settings settings) {
        return limitSeconds(settings) - usedSeconds;
    }

    synchronized long tick(Settings settings) {
        rollDay(settings);
        usedSeconds++;
        return settings.limitSeconds(limitMinutes, day) - usedSeconds;
    }
}
