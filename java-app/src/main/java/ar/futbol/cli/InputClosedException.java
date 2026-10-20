package ar.futbol.cli;

/** The user closed the input (Ctrl+Z on Windows, Ctrl+D elsewhere) or the piped input ran out. */
public final class InputClosedException extends RuntimeException {

    public InputClosedException() {
        super("input ended");
    }
}
