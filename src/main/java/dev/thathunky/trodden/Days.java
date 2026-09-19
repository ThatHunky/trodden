package dev.thathunky.trodden;

import java.time.LocalDate;
import java.time.ZoneId;

/** Day numbers used by the counters: days since the epoch in the server's own time zone. */
final class Days {

    private Days() {
    }

    static int today() {
        return (int) LocalDate.now(ZoneId.systemDefault()).toEpochDay();
    }
}
