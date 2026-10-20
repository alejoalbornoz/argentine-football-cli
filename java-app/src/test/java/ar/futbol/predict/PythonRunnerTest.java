package ar.futbol.predict;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PythonRunnerTest {

    static final Path PROJECT_ROOT = Path.of(".."); // Maven runs the tests from java-app/
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @TempDir
    Path dir;

    /** The project's .venv interpreter; tests that need a real Python are skipped without it. */
    static String projectPython() {
        String python = PythonRunner.findPython(PROJECT_ROOT);
        assumeTrue(!python.equals("python"), "needs the project's .venv (see README)");
        return python;
    }

    private Path script(String code) throws Exception {
        return Files.writeString(dir.resolve("script.py"), code);
    }

    @Test
    void returnsWhatTheScriptPrints() throws Exception {
        Path script = script("import sys\nprint('args:', *sys.argv[1:])\n");

        String output = new PythonRunner(projectPython()).run(script, TIMEOUT, "--db", "two words");

        assertEquals("args: --db two words", output.strip());
    }

    @Test
    void aFailingScriptsMessageOnStderrBecomesTheError() throws Exception {
        Path script = script("import sys\nprint('equipo desconocido: Nadie', file=sys.stderr)\nsys.exit(1)\n");

        var error = assertThrows(PredictionException.class,
                () -> new PythonRunner(projectPython()).run(script, TIMEOUT));
        assertEquals("equipo desconocido: Nadie", error.getMessage());
    }

    @Test
    void aScriptThatTakesTooLongIsStopped() throws Exception {
        Path script = script("import time\ntime.sleep(30)\n");

        var error = assertThrows(PredictionException.class,
                () -> new PythonRunner(projectPython()).run(script, Duration.ofSeconds(1)));
        assertEquals("Python no respondió a tiempo (límite: 1 s)", error.getMessage());
    }

    @Test
    void missingScriptIsReported() {
        var runner = new PythonRunner("python");

        var error = assertThrows(PredictionException.class, () -> runner.run(dir.resolve("missing.py"), TIMEOUT));
        assertTrue(error.getMessage().startsWith("no se encontró el script de Python"), error.getMessage());
    }

    @Test
    void missingInterpreterIsReported() throws Exception {
        Path script = script("");
        var runner = new PythonRunner("no-such-python-here");

        var error = assertThrows(PredictionException.class, () -> runner.run(script, TIMEOUT));
        assertTrue(error.getMessage().startsWith("no se pudo iniciar Python (no-such-python-here)"),
                error.getMessage());
    }
}
