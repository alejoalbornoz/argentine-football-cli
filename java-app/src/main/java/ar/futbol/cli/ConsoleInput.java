package ar.futbol.cli;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.function.Function;

/** Reads the user's choices. Bad input never escapes: it prints a hint and asks again. */
public final class ConsoleInput {

    private static final int MAX_ROWS_IN_ONE_COLUMN = 10;
    private static final int LIST_COLUMNS = 3;

    private final Scanner scanner;
    private final PrintStream out;

    public ConsoleInput(InputStream in, PrintStream out) {
        this.scanner = new Scanner(in);
        this.out = out;
    }

    /**
     * Asks until the user types a whole number from {@code min} to {@code max}.
     *
     * @throws InputClosedException if the input ends before a valid answer
     */
    public int readInt(String prompt, int min, int max) {
        while (true) {
            out.print(prompt + " (" + min + "-" + max + "): ");
            out.flush();
            if (!scanner.hasNextLine()) {
                throw new InputClosedException();
            }
            String answer = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(answer);
                if (value >= min && value <= max) {
                    return value;
                }
            } catch (NumberFormatException e) {
                // not a number (or too long to be an int): fall through to the hint
            }
            out.println("Invalid choice. Type a number from " + min + " to " + max + ".");
        }
    }

    /** Shows a numbered list and returns the chosen option, or empty if the user picks 0 to go back. */
    public Optional<String> choose(String title, List<String> options) {
        return choose(title, options, Function.identity());
    }

    /** Like {@link #choose(String, List)} for any kind of option, each shown as {@code label} gives it. */
    public <T> Optional<T> choose(String title, List<T> options, Function<T, String> label) {
        out.println();
        out.println(title);
        printNumbered(options.stream().map(label).toList());
        out.printf("%" + numberWidth(options) + "d. Back%n", 0);
        int choice = readInt("Choose", 0, options.size());
        return choice == 0 ? Optional.empty() : Optional.of(options.get(choice - 1));
    }

    /** Long lists go in columns, filled top to bottom, so 44 teams fit on one screen. */
    private void printNumbered(List<String> options) {
        int columns = options.size() > MAX_ROWS_IN_ONE_COLUMN ? LIST_COLUMNS : 1;
        int rows = (options.size() + columns - 1) / columns;
        int textWidth = options.stream().mapToInt(String::length).max().orElse(0);
        String cell = "%" + numberWidth(options) + "d. %-" + textWidth + "s   ";

        for (int row = 0; row < rows; row++) {
            StringBuilder line = new StringBuilder();
            for (int column = 0; column < columns; column++) {
                int index = column * rows + row;
                if (index < options.size()) {
                    line.append(String.format(cell, index + 1, options.get(index)));
                }
            }
            out.println(line.toString().stripTrailing());
        }
    }

    private static int numberWidth(List<?> options) {
        return String.valueOf(options.size()).length();
    }
}
