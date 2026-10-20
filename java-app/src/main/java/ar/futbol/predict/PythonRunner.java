package ar.futbol.predict;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Runs the scripts in {@code python/} as separate processes.
 *
 * <p>The contract with every script: success is exit code 0 with the answer as JSON on stdout;
 * on failure the script exits with another code and explains why on stderr.
 */
public final class PythonRunner {

    /** The scripts print snake_case keys ({@code home_win}); records use camelCase ({@code homeWin}). */
    static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    private final String python;

    /** @param python the interpreter to run, e.g. from {@link #findPython(Path)} */
    public PythonRunner(String python) {
        this.python = python;
    }

    /**
     * The interpreter to use: {@code FUTBOL_PYTHON} if set, else the project's {@code .venv}
     * (Windows or Linux/macOS layout), else whatever {@code python} is on the PATH.
     */
    public static String findPython(Path projectRoot) {
        String configured = System.getenv("FUTBOL_PYTHON");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        for (Path candidate : List.of(projectRoot.resolve(".venv/Scripts/python.exe"),
                                      projectRoot.resolve(".venv/bin/python"))) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().toString();
            }
        }
        return "python";
    }

    /** Runs {@code script} with {@code arguments} and returns what it printed on stdout. */
    String run(Path script, Duration timeout, String... arguments) throws PredictionException {
        if (!Files.isRegularFile(script)) {
            throw new PredictionException("no se encontró el script de Python: " + script.toAbsolutePath());
        }
        List<String> command = new ArrayList<>(List.of(python, script.toString()));
        command.addAll(List.of(arguments));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("PYTHONIOENCODING", "utf-8");

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new PredictionException("no se pudo iniciar Python (" + python + "): " + e.getMessage());
        }

        // Read both streams while Python runs: if a pipe fills up unread, the process blocks forever.
        CompletableFuture<String> output = readAll(process.getInputStream());
        CompletableFuture<String> errors = readAll(process.getErrorStream());
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new PredictionException("Python no respondió a tiempo (límite: " + timeout.toSeconds() + " s)");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new PredictionException("se interrumpió la espera de Python");
        }

        if (process.exitValue() != 0) {
            String message = errors.join().strip();
            throw new PredictionException(
                    message.isEmpty() ? "Python terminó con el código " + process.exitValue() : message);
        }
        return output.join();
    }

    private static CompletableFuture<String> readAll(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (stream) {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
