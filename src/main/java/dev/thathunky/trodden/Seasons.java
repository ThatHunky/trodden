package dev.thathunky.trodden;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Season calendar: four start dates, one per season. The season of a date is the one whose start
 * is the latest start not after that date, wrapping around the new year.
 *
 * <p>Optionally backed by MatsuriSeasons's {@code season.json}: when a path is given, {@link #at}
 * tries that file first and falls back to the dates only if it is missing or unparseable. The file
 * is re-read at most once per {@value #MIN_READ_INTERVAL_MILLIS}ms so a sweep touching many chunks
 * costs at most one disk read, and a read failure is logged once, not on every attempt.
 */
final class Seasons {

    private static final long MIN_READ_INTERVAL_MILLIS = 60_000;

    private final Map<Season, MonthDay> starts;
    private final String jsonPath;
    private final Logger logger;

    private volatile long lastAttemptMillis = Long.MIN_VALUE;
    private volatile Season cachedJsonSeason;
    private volatile boolean warnedUnreadable;

    Seasons(Map<Season, MonthDay> starts) {
        this(starts, null, null);
    }

    /** {@code jsonPath} may be null or blank to disable the file source entirely. */
    Seasons(Map<Season, MonthDay> starts, String jsonPath, Logger logger) {
        if (starts.size() != Season.values().length) {
            throw new IllegalArgumentException("need a start date for every season, got " + starts);
        }
        this.starts = new EnumMap<>(starts);
        this.jsonPath = (jsonPath == null || jsonPath.isBlank()) ? null : jsonPath;
        this.logger = logger;
    }

    /**
     * Parses the tiny {@code season.json} MatsuriSeasons publishes, e.g. {@code {"id": 3, "key": "autumn", ...}}.
     * No JSON library on the classpath, so this is a plain search for {@code "key"} and the quoted value after
     * it. Total: returns null for anything it cannot make sense of, and never throws.
     */
    static Season fromJson(String text) {
        if (text == null) {
            return null;
        }
        int keyIdx = text.indexOf("\"key\"");
        if (keyIdx < 0) {
            return null;
        }
        int colon = text.indexOf(':', keyIdx + 5);
        if (colon < 0) {
            return null;
        }
        int openQuote = text.indexOf('"', colon + 1);
        if (openQuote < 0) {
            return null;
        }
        int closeQuote = text.indexOf('"', openQuote + 1);
        if (closeQuote < 0) {
            return null;
        }
        String value = text.substring(openQuote + 1, closeQuote);
        return switch (value) {
            case "spring" -> Season.SPRING;
            case "summer" -> Season.SUMMER;
            case "autumn" -> Season.AUTUMN;
            case "winter" -> Season.WINTER;
            default -> null;
        };
    }

    static Seasons ofDefaults() {
        return new Seasons(Map.of(
                Season.SPRING, MonthDay.of(3, 1),
                Season.SUMMER, MonthDay.of(6, 1),
                Season.AUTUMN, MonthDay.of(9, 1),
                Season.WINTER, MonthDay.of(12, 1)));
    }

    Season at(LocalDate date) {
        if (jsonPath != null) {
            Season fromFile = readJsonSeason();
            if (fromFile != null) {
                return fromFile;
            }
        }
        return atFromDates(date);
    }

    private Season readJsonSeason() {
        long now = System.currentTimeMillis();
        if (now - lastAttemptMillis < MIN_READ_INTERVAL_MILLIS) {
            return cachedJsonSeason;
        }
        lastAttemptMillis = now;
        Season parsed = null;
        try {
            parsed = fromJson(Files.readString(Path.of(jsonPath)));
        } catch (IOException | RuntimeException e) {
            // treated as "unreadable" below, same as a file that parses to nothing
        }
        cachedJsonSeason = parsed;
        if (parsed != null) {
            warnedUnreadable = false;
        } else if (!warnedUnreadable && logger != null) {
            logger.warning("Could not read the season from " + jsonPath + " — using the config dates instead.");
            warnedUnreadable = true;
        }
        return parsed;
    }

    private Season atFromDates(LocalDate date) {
        MonthDay today = MonthDay.from(date);
        Season best = null;
        MonthDay bestStart = null;
        Season latest = null;
        MonthDay latestStart = null;
        for (Map.Entry<Season, MonthDay> e : starts.entrySet()) {
            MonthDay start = e.getValue();
            if (!start.isAfter(today) && (bestStart == null || start.isAfter(bestStart))) {
                best = e.getKey();
                bestStart = start;
            }
            if (latestStart == null || start.isAfter(latestStart)) {
                latest = e.getKey();
                latestStart = start;
            }
        }
        return best != null ? best : latest; // before the first start of the year → last season of the previous one
    }
}
