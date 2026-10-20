package ar.futbol.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleInputTest {

    private final ByteArrayOutputStream printed = new ByteArrayOutputStream();

    private ConsoleInput typing(String typed) {
        return new ConsoleInput(new ByteArrayInputStream(typed.getBytes(UTF_8)),
                new PrintStream(printed, true, UTF_8));
    }

    private String output() {
        return printed.toString(UTF_8);
    }

    @Test
    void keepsAskingUntilTheAnswerIsAValidNumber() {
        // empty, text, decimal, too big for an int, out of range, then a valid answer with spaces
        int value = typing("\nabc\n2.5\n99999999999\n7\n 3 \n").readInt("Pick", 1, 5);

        assertEquals(3, value);
        assertEquals(5, occurrences(output(), "Opción inválida. Escribí un número del 1 al 5."));
    }

    @Test
    void endOfInputIsReportedAsInputClosed() {
        assertThrows(InputClosedException.class, () -> typing("").readInt("Pick", 1, 5));
        assertThrows(InputClosedException.class, () -> typing("abc\n").readInt("Pick", 1, 5));
    }

    @Test
    void chooseReturnsThePickedOptionOrEmptyForBack() {
        List<String> teams = List.of("Alpha", "Beta");

        assertEquals(Optional.of("Beta"), typing("2\n").choose("Team:", teams));
        assertEquals(Optional.empty(), typing("0\n").choose("Team:", teams));
    }

    @Test
    void chooseWorksWithAnyTypeShownThroughALabel() {
        Optional<Integer> picked = typing("2\n").choose("Size:", List.of(10, 20), size -> size + " cm");

        assertEquals(Optional.of(20), picked);
        assertTrue(output().contains("1. 10 cm"));
        assertTrue(output().contains("2. 20 cm"));
    }

    @Test
    void chooseNumbersTheOptionsAndOffersBack() {
        typing("1\n").choose("Team:", List.of("Alpha", "Beta"));

        assertTrue(output().contains("1. Alpha"));
        assertTrue(output().contains("2. Beta"));
        assertTrue(output().contains("0. Volver"));
    }

    @Test
    void longListsAreShownInColumnsFilledTopToBottom() {
        List<String> options = IntStream.rangeClosed(1, 12).mapToObj(i -> "T" + i).toList();

        typing("1\n").choose("Team:", options);

        assertTrue(output().contains(" 1. T1     5. T5     9. T9"), output());
        assertTrue(output().contains(" 4. T4     8. T8    12. T12"), output());
    }

    private static int occurrences(String text, String part) {
        return text.split(Pattern.quote(part), -1).length - 1;
    }
}
