package ar.futbol.cli;

import ar.futbol.data.MatchRepository;
import ar.futbol.model.Match;
import ar.futbol.predict.Backtester;
import ar.futbol.predict.Model;
import ar.futbol.predict.PredictionException;
import ar.futbol.predict.Predictor;
import ar.futbol.stats.Standings;
import ar.futbol.stats.TeamStats;

import java.io.PrintStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The interactive main menu. Runs until the user picks 0 or the input ends. */
public final class Menu {

    private static final List<String> OPTIONS = List.of(
            "Standings", "Team stats", "Head-to-head", "Predict a match", "Evaluate models (backtest)");
    private static final String ALL_SEASONS = "All seasons";

    private record TeamPair(String first, String second) {}

    private final MatchRepository repository;
    private final Predictor predictor;
    private final Backtester backtester;
    private final ConsoleInput input;
    private final PrintStream out;

    public Menu(MatchRepository repository, Predictor predictor, Backtester backtester,
                ConsoleInput input, PrintStream out) {
        this.repository = repository;
        this.predictor = predictor;
        this.backtester = backtester;
        this.input = input;
        this.out = out;
    }

    public void run() {
        try {
            int option;
            while ((option = askMainOption()) != 0) {
                try {
                    switch (option) {
                        case 1 -> showStandings();
                        case 2 -> showTeamStats();
                        case 3 -> showHeadToHead();
                        case 4 -> predictMatch();
                        case 5 -> evaluateModels();
                        default -> throw new IllegalStateException("no action for option " + option);
                    }
                } catch (SQLException e) {
                    out.println("Database error: " + e.getMessage());
                }
            }
        } catch (InputClosedException e) {
            out.println(); // the input ended mid-prompt: finish the line and leave
        }
        out.println("Bye!");
    }

    private int askMainOption() {
        out.println();
        out.println("=== Argentine Football CLI ===");
        for (int i = 0; i < OPTIONS.size(); i++) {
            out.println((i + 1) + ". " + OPTIONS.get(i));
        }
        out.println("0. Exit");
        return input.readInt("Choose an option", 0, OPTIONS.size());
    }

    private void showStandings() throws SQLException {
        Optional<String> season = input.choose("Season:", repository.findSeasons());
        if (season.isEmpty()) {
            return;
        }
        List<String> competitions = repository.findCompetitions(season.get());
        Optional<String> competition = competitions.size() == 1
                ? Optional.of(competitions.getFirst())
                : input.choose("Competition:", competitions);
        if (competition.isEmpty()) {
            return;
        }

        List<Match> matches = repository.findBySeasonAndCompetition(season.get(), competition.get());
        out.println();
        out.print(Reports.standings(competition.get() + " " + season.get(),
                Standings.table(matches), matches.size()));
    }

    private void showTeamStats() throws SQLException {
        Optional<String> team = input.choose("Team:", repository.findTeams());
        if (team.isEmpty()) {
            return;
        }
        List<Match> matches = repository.findByTeam(team.get());
        List<String> scopes = new ArrayList<>();
        scopes.add(ALL_SEASONS);
        scopes.addAll(matches.stream().map(Match::season).distinct().toList()); // chronological
        Optional<String> scope = input.choose("Season:", scopes);
        if (scope.isEmpty()) {
            return;
        }

        List<Match> selected = scope.get().equals(ALL_SEASONS)
                ? matches
                : matches.stream().filter(m -> m.season().equals(scope.get())).toList();
        out.println();
        out.print(Reports.teamStats(TeamStats.of(team.get(), selected), scope.get()));
    }

    private void showHeadToHead() throws SQLException {
        Optional<TeamPair> teams = chooseTwoTeams("First team:", "Second team:");
        if (teams.isEmpty()) {
            return;
        }
        String first = teams.get().first();
        String second = teams.get().second();
        out.println();
        out.print(Reports.headToHead(first, second, repository.findHeadToHead(first, second)));
    }

    private void predictMatch() throws SQLException {
        Optional<TeamPair> teams = chooseTwoTeams("Home team:", "Away team:");
        if (teams.isEmpty()) {
            return;
        }
        Optional<Model> model = input.choose("Model:", List.of(Model.values()), Model::menuText);
        if (model.isEmpty()) {
            return;
        }

        out.println();
        out.println("Running the " + model.get().label() + " model...");
        try {
            out.print(Reports.prediction(
                    predictor.predict(model.get(), teams.get().first(), teams.get().second())));
        } catch (PredictionException e) {
            out.println("Prediction failed: " + e.getMessage());
        }
    }

    private void evaluateModels() {
        out.println();
        out.println("Running the backtest: replaying past seasons takes about 15 seconds...");
        try {
            out.print(Reports.backtest(backtester.run()));
        } catch (PredictionException e) {
            out.println("Backtest failed: " + e.getMessage());
        }
    }

    /** Two different teams; the second list leaves out the first pick. Empty if the user goes back. */
    private Optional<TeamPair> chooseTwoTeams(String firstTitle, String secondTitle) throws SQLException {
        List<String> teams = repository.findTeams();
        Optional<String> first = input.choose(firstTitle, teams);
        if (first.isEmpty()) {
            return Optional.empty();
        }
        List<String> others = teams.stream().filter(team -> !team.equals(first.get())).toList();
        return input.choose(secondTitle, others).map(second -> new TeamPair(first.get(), second));
    }
}
