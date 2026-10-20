package ar.futbol.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A played match with its final score.
 *
 * <p>The compact constructor enforces the invariants, so any {@code Match} that exists is valid.
 * {@code IllegalArgumentException} messages are written to be shown to the user.
 */
public record Match(
        String season,
        String competition,
        LocalDate date,
        String homeTeam,
        String awayTeam,
        int homeGoals,
        int awayGoals) {

    public Match {
        requireText(season, "season");
        requireText(competition, "competition");
        Objects.requireNonNull(date, "date");
        requireText(homeTeam, "home_team");
        requireText(awayTeam, "away_team");
        if (homeGoals < 0 || awayGoals < 0) {
            throw new IllegalArgumentException("goals cannot be negative");
        }
        if (homeTeam.equals(awayTeam)) {
            throw new IllegalArgumentException("a team cannot play itself (" + homeTeam + ")");
        }
    }

    public boolean involves(String team) {
        return homeTeam.equals(team) || awayTeam.equals(team);
    }

    public boolean isHomeTeam(String team) {
        return homeTeam.equals(team);
    }

    public int goalsFor(String team) {
        requireInvolved(team);
        return isHomeTeam(team) ? homeGoals : awayGoals;
    }

    public int goalsAgainst(String team) {
        requireInvolved(team);
        return isHomeTeam(team) ? awayGoals : homeGoals;
    }

    /** The result from {@code team}'s point of view. */
    public Outcome outcomeFor(String team) {
        int difference = goalsFor(team) - goalsAgainst(team);
        if (difference > 0) {
            return Outcome.WIN;
        }
        return difference < 0 ? Outcome.LOSS : Outcome.DRAW;
    }

    private void requireInvolved(String team) {
        if (!involves(team)) {
            throw new IllegalArgumentException(team + " did not play " + homeTeam + " vs " + awayTeam);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is empty");
        }
    }
}
