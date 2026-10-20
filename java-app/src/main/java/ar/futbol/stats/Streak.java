package ar.futbol.stats;

import java.time.LocalDate;

/** A run of consecutive matches, from the date of the first to the date of the last. */
public record Streak(int length, LocalDate from, LocalDate to) {

    public static final Streak NONE = new Streak(0, null, null);
}
