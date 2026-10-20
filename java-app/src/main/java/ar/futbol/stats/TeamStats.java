package ar.futbol.stats;

import ar.futbol.model.Match;
import ar.futbol.model.Outcome;

import java.util.List;
import java.util.function.Predicate;

/**
 * One team's numbers over a set of matches: home/away split, recent form and streaks.
 *
 * @param lastFive       outcomes of the latest (up to) five matches, oldest first
 * @param currentOutcome outcome of the latest match, or {@code null} if there are no matches
 * @param currentRun     consecutive {@code currentOutcome}s up to the latest match
 */
public record TeamStats(
        String team,
        Totals home,
        Totals away,
        Totals total,
        List<Outcome> lastFive,
        Outcome currentOutcome,
        Streak currentRun,
        Streak longestWinningRun,
        Streak longestUnbeatenRun,
        Streak longestLosingRun) {

    /** {@code matches} must be in chronological order; matches without {@code team} are ignored. */
    public static TeamStats of(String team, List<Match> matches) {
        List<Match> played = matches.stream().filter(m -> m.involves(team)).toList();
        List<Outcome> outcomes = played.stream().map(m -> m.outcomeFor(team)).toList();
        Outcome latest = outcomes.isEmpty() ? null : outcomes.getLast();

        return new TeamStats(
                team,
                Totals.of(team, played.stream().filter(m -> m.isHomeTeam(team)).toList()),
                Totals.of(team, played.stream().filter(m -> !m.isHomeTeam(team)).toList()),
                Totals.of(team, played),
                List.copyOf(outcomes.subList(Math.max(0, outcomes.size() - 5), outcomes.size())),
                latest,
                currentRun(played, outcomes, outcome -> outcome == latest),
                longestRun(played, outcomes, outcome -> outcome == Outcome.WIN),
                longestRun(played, outcomes, outcome -> outcome != Outcome.LOSS),
                longestRun(played, outcomes, outcome -> outcome == Outcome.LOSS));
    }

    /** Longest run of consecutive matches whose outcome passes {@code test}; on a tie, the earliest. */
    private static Streak longestRun(List<Match> matches, List<Outcome> outcomes, Predicate<Outcome> test) {
        Streak best = Streak.NONE;
        int start = 0;
        for (int i = 0; i < outcomes.size(); i++) {
            if (!test.test(outcomes.get(i))) {
                start = i + 1;
            } else if (i - start + 1 > best.length()) {
                best = new Streak(i - start + 1, matches.get(start).date(), matches.get(i).date());
            }
        }
        return best;
    }

    /** The run still going: consecutive matches passing {@code test}, counting back from the latest. */
    private static Streak currentRun(List<Match> matches, List<Outcome> outcomes, Predicate<Outcome> test) {
        int start = outcomes.size();
        while (start > 0 && test.test(outcomes.get(start - 1))) {
            start--;
        }
        if (start == outcomes.size()) {
            return Streak.NONE;
        }
        return new Streak(outcomes.size() - start, matches.get(start).date(), matches.getLast().date());
    }
}
