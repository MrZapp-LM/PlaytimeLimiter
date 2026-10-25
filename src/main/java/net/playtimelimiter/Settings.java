package net.playtimelimiter;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;

/** Values from config.yml. */
record Settings(
        LocalTime resetTime,
        ZoneId zone,
        int weekendBonus,
        List<Long> warnAt,
        String kickMessage,
        String warningMessage) {

    static Settings from(FileConfiguration config, Logger logger) {
        LocalTime resetTime = LocalTime.MIDNIGHT;
        String reset = config.getString("reset-time", "00:00");
        try {
            resetTime = LocalTime.parse(reset);
        } catch (DateTimeParseException e) {
            logger.warning("Invalid reset-time '" + reset + "', using 00:00.");
        }

        ZoneId zone = ZoneId.systemDefault();
        String tz = config.getString("timezone", "");
        if (!tz.isBlank()) {
            try {
                zone = ZoneId.of(tz);
            } catch (DateTimeException e) {
                logger.warning("Invalid timezone '" + tz + "', using " + zone + ".");
            }
        }

        return new Settings(
                resetTime,
                zone,
                Math.max(0, config.getInt("weekend-bonus", 0)),
                config.getLongList("warn-at"),
                config.getString("messages.kick", ""),
                config.getString("messages.warning", ""));
    }

    /** The current "play day": the date on which the latest reset happened. */
    LocalDate today() {
        return ZonedDateTime.now(zone)
                .minusHours(resetTime.getHour())
                .minusMinutes(resetTime.getMinute())
                .toLocalDate();
    }

    /** A player's limit for the given play day, including the weekend bonus. */
    long limitSeconds(int limitMinutes, LocalDate day) {
        boolean weekend = day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
        return (limitMinutes + (weekend ? weekendBonus : 0)) * 60L;
    }
}
