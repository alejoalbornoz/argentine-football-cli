package ar.futbol.stats;

import ar.futbol.model.Match;
import ar.futbol.model.Outcome;

import java.util.List;

/** Played / won / drawn / lost and goals of one team over a set of matches. */
public record Totals(int played, int won, int drawn, int lost, int goalsFor, int goalsAgainst) {

    public static final Totals NONE = new Totals(0, 0, 0, 0, 0, 0);

    /** Totals of {@code team} over the matches in {@code matches} that it played. */
    public static Totals of(String team, List<Match> matches) {
        Totals totals = NONE;
        for (Match match : matches) {
            if (match.involves(team)) {
                totals = totals.plus(match, team);
            }
        }
        return totals;
    }

    /** These totals with one more match added, seen from {@code team}'s side. */
    public Totals plus(Match match, String team) {
        Outcome outcome = match.outcomeFor(team);
        return new Totals(
                played + 1,
                won + (outcome == Outcome.WIN ? 1 : 0),
                drawn + (outcome == Outcome.DRAW ? 1 : 0),
                lost + (outcome == Outcome.LOSS ? 1 : 0),
                goalsFor + match.goalsFor(team),
                goalsAgainst + match.goalsAgainst(team));
    }

    public int points() {
        return 3 * won + drawn;
    }

    public int goalDifference() {
        return goalsFor - goalsAgainst;
    }
}
