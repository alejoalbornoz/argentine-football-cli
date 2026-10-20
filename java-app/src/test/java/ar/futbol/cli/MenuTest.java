package ar.futbol.cli;

import ar.futbol.data.Database;
import ar.futbol.data.MatchRepository;
import ar.futbol.model.Match;
import ar.futbol.predict.BacktestReport;
import ar.futbol.predict.Backtester;
import ar.futbol.predict.Prediction;
import ar.futbol.predict.PredictionException;
import ar.futbol.predict.Predictor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives the whole menu with scripted keyboard input against a real (temporary) database. */
class MenuTest {

    private static final String MAIN_MENU = "=== Fútbol argentino: estadísticas y predicciones ===";

    /** Answers with the chosen model's name, so tests can see which model the menu asked for. */
    private static final Predictor FAKE_PREDICTOR = (model, home, away) ->
            new Prediction(model.label() + " (fake)", home, away, 0.5, 0.3, 0.2, Map.of("Nota", "del falso"));

    private static final Backtester FAKE_BACKTESTER = () -> new BacktestReport(
            "2023-01-27", "2026-10-20", 2122, 4308,
            new BacktestReport.Outcomes(0.426, 0.320, 0.254),
            List.of(new BacktestReport.Score("Elo", 1.0672, 0.6453, 0.422),
                    new BacktestReport.Score("Base: siempre gana el local", 1.0777, 0.6526, 0.426)));

    private MatchRepository repository;

    @BeforeEach
    void setUp(@TempDir Path dir) throws IOException, SQLException {
        repository = new MatchRepository(Database.open(dir.resolve("test.db")));
        repository.replaceAll(List.of(
                match("2024", "League", "2024-02-10", "Alpha", "Beta", 2, 1),
                match("2024", "League", "2024-02-17", "Beta", "Gamma", 0, 0),
                match("2024", "Cup", "2024-03-02", "Gamma", "Alpha", 1, 3),
                match("2025", "League", "2025-02-01", "Alpha", "Gamma", 0, 1),
                match("2024", "Cup", "2024-03-09", "Delta", "Epsilon", 0, 0)));
        // Teams, as listed in the menu: 1 Alpha, 2 Beta, 3 Delta, 4 Epsilon, 5 Gamma
    }

    /** Runs the menu as if the user typed {@code typed}, and returns everything it printed. */
    private String run(String typed) {
        return run(typed, FAKE_PREDICTOR, FAKE_BACKTESTER);
    }

    private String run(String typed, Predictor predictor, Backtester backtester) {
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(printed, true, UTF_8);
        ConsoleInput input = new ConsoleInput(new ByteArrayInputStream(typed.getBytes(UTF_8)), out);
        new Menu(repository, predictor, backtester, input, out).run();
        return printed.toString(UTF_8);
    }

    @Test
    void badInputIsRejectedAndTheMenuKeepsGoing() {
        String output = run("abc\n\n-1\n9\n0\n");

        assertEquals(4, occurrences(output, "Opción inválida. Escribí un número del 0 al 5."));
        assertTrue(output.contains("¡Chau!"));
    }

    @Test
    void standingsAskForTheCompetitionWhenASeasonHasSeveral() {
        // 1 = standings, 1 = season 2024, 2 = "League" ("Cup" sorts first), 0 = exit
        String output = run("1\n1\n2\n0\n");

        assertTrue(output.contains("Competición:"));
        assertTrue(output.contains("League 2024 - 2 partidos"));
        // Alpha won; Gamma's draw beats Beta's draw-and-loss on goal difference
        assertTrue(Pattern.compile("(?m)^1  Alpha .*\\+1 +3$").matcher(output).find(), output);
        assertTrue(Pattern.compile("(?m)^2  Gamma ").matcher(output).find(), output);
        assertTrue(Pattern.compile("(?m)^3  Beta ").matcher(output).find(), output);
    }

    @Test
    void standingsSkipTheCompetitionQuestionWhenThereIsOnlyOne() {
        // 1 = standings, 2 = season 2025 (league only), 0 = exit
        String output = run("1\n2\n0\n");

        assertFalse(output.contains("Competición:"));
        assertTrue(output.contains("League 2025 - 1 partido\n"));
    }

    @Test
    void teamStatsCoverAllSeasonsAndCompetitions() {
        // 2 = team stats, 1 = Alpha, 1 = all seasons, 0 = exit
        String output = run("2\n1\n1\n0\n");

        assertTrue(output.contains("Alpha - Todas las temporadas (todas las competiciones)"), output);
        assertTrue(Pattern.compile("(?m)^Últimos 5 partidos +G G P  \\(").matcher(output).find(), output);
        assertTrue(Pattern.compile("(?m)^Racha actual +1 derrota \\(desde el 01/02/2025\\)$")
                .matcher(output).find(), output);
        assertTrue(Pattern.compile("(?m)^Mayor racha ganadora +2 partidos \\(del 10/02/2024 al 02/03/2024\\)$")
                .matcher(output).find(), output);
    }

    @Test
    void teamStatsCanBeLimitedToOneSeason() {
        // 2 = team stats, 1 = Alpha, 3 = season 2025 (after "Todas las temporadas" and 2024), 0 = exit
        String output = run("2\n1\n3\n0\n");

        assertTrue(output.contains("Alpha - 2025 (todas las competiciones)"));
        assertTrue(Pattern.compile("(?m)^Últimos 5 partidos +P  \\(").matcher(output).find(), output);
    }

    @Test
    void zeroInASubmenuGoesBackToTheMainMenu() {
        String output = run("1\n0\n2\n0\n0\n");

        assertEquals(3, occurrences(output, MAIN_MENU));
        assertFalse(output.contains("partidos"));
    }

    @Test
    void inputEndingMidQuestionExitsCleanly() {
        String output = run("1\n"); // picks standings, then the input ends at the season question

        assertTrue(output.contains("Temporada:"));
        assertTrue(output.contains("¡Chau!"));
    }

    @Test
    void headToHeadShowsTheRecordAndTheLatestMeetingsFirst() {
        // 3 = head-to-head, 1 = Alpha, 4 = Gamma (the second list leaves Alpha out), 0 = exit
        String output = run("3\n1\n4\n0\n");

        assertTrue(output.contains("Alpha vs Gamma - 2 partidos, del 02/03/2024 al 01/02/2025"), output);
        assertTrue(Pattern.compile("(?m)^Alpha de local +1 +0 +0 +1 +0 +1$").matcher(output).find(), output);
        assertTrue(Pattern.compile("(?m)^Gamma de local +1 +1 +0 +0 +3 +1$").matcher(output).find(), output);
        assertTrue(Pattern.compile("(?m)^Total +2 +1 +0 +1 +3 +2$").matcher(output).find(), output);
        assertTrue(output.indexOf("01/02/2025  League") < output.indexOf("02/03/2024  Cup"), output);
    }

    @Test
    void headToHeadSaysSoWhenTheTeamsNeverMet() {
        // 3 = head-to-head, 1 = Alpha, 2 = Delta, 0 = exit
        String output = run("3\n1\n2\n0\n");

        assertTrue(output.contains("Alpha y Delta no se enfrentaron en estos datos."));
    }

    @Test
    void predictionShowsEachOutcomeWithItsProbability() {
        // 4 = predict, 1 = Alpha at home, 1 = Beta away, 1 = Elo, 0 = exit
        String output = run("4\n1\n1\n1\n0\n");

        assertTrue(output.contains("Calculando con el modelo Elo..."), output);
        assertTrue(output.contains("Alpha (local) vs Beta - modelo Elo (fake)"), output);
        assertTrue(Pattern.compile("(?m)^Gana Alpha +50,0% +#{20}$").matcher(output).find(), output);
        assertTrue(Pattern.compile("(?m)^Empate +30,0% +#{12}$").matcher(output).find(), output);
        assertTrue(Pattern.compile("(?m)^Gana Beta +20,0% +#{8}$").matcher(output).find(), output);
        assertTrue(output.contains("Nota del falso"));
    }

    @Test
    void aFailedPredictionIsReportedAndTheMenuKeepsGoing() {
        Predictor broken = (model, home, away) -> {
            throw new PredictionException("Python no está instalado");
        };

        String output = run("4\n1\n1\n1\n0\n", broken, FAKE_BACKTESTER);

        assertTrue(output.contains("No se pudo predecir: Python no está instalado"));
        assertTrue(output.contains("¡Chau!"));
    }

    @Test
    void theModelIsChosenAfterTheTeams() {
        // 4 = predict, 1 = Alpha at home, 1 = Beta away, 2 = Poisson, 0 = exit
        String output = run("4\n1\n1\n2\n0\n");

        assertTrue(output.indexOf("Equipo visitante:") < output.indexOf("Modelo:"), output);
        assertTrue(output.contains("1. Elo - ratings que se ajustan después de cada resultado"));
        assertTrue(output.contains("2. Poisson - goles esperados según ataque y defensa"));
        assertTrue(output.contains("Calculando con el modelo Poisson..."));
        assertTrue(output.contains("Alpha (local) vs Beta - modelo Poisson (fake)"));
    }

    @Test
    void zeroAtTheModelQuestionGoesBackWithoutPredicting() {
        String output = run("4\n1\n1\n0\n0\n");

        assertFalse(output.contains("Calculando"));
        assertEquals(2, occurrences(output, MAIN_MENU));
    }

    @Test
    void backtestShowsEachModelsScoresNextToTheBaseline() {
        String output = run("5\n0\n");

        assertTrue(output.contains("Backtest: 2122 partidos del 27/01/2023 al 20/10/2026"), output);
        assertTrue(output.contains("(los 4308 partidos previos sirvieron solo para calentar los modelos)"), output);
        assertTrue(Pattern.compile("(?m)^Elo +1,067 +0,645 +42,2%$").matcher(output).find(), output);
        assertTrue(Pattern.compile("(?m)^Base: siempre gana el local +1,078 +0,653 +42,6%$")
                .matcher(output).find(), output);
        assertTrue(output.contains("victorias locales 42,6%, empates 32,0%, victorias visitantes 25,4%."), output);
    }

    @Test
    void aFailedBacktestIsReportedAndTheMenuKeepsGoing() {
        Backtester broken = () -> {
            throw new PredictionException("no hay partidos antes del 2023-01-01 para calentar los modelos");
        };

        String output = run("5\n0\n", FAKE_PREDICTOR, broken);

        assertTrue(output.contains("Falló el backtest: no hay partidos antes del 2023-01-01 para calentar los modelos"));
        assertTrue(output.contains("¡Chau!"));
    }

    private static Match match(String season, String competition, String date,
                               String home, String away, int homeGoals, int awayGoals) {
        return new Match(season, competition, LocalDate.parse(date), home, away, homeGoals, awayGoals);
    }

    private static int occurrences(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }
}
