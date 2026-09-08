package org.xvm.compiler;

import org.xvm.tool.Launcher.LauncherException;

/**
 * A non-fatal exception that can be emitted during any stage of the compilation
 * process to indicate that forward progress is not possible due to a flaw in
 * source material being compiled.
 */
public class CompilerException extends LauncherException {
    /** Never Java-serialized; present so the serial lint stays clean. */
    private static final long serialVersionUID = 1L;

    public CompilerException(String message) {
        super(true, message);
    }

    /**
     * Construct a CompilerException that carries what actually went wrong underneath.
     *
     * <p>Without this, a caller holding both a message and a cause had to pick one, and picking the
     * message is the easy choice - which is how {@code Parser} came to report "no such directory or
     * file" for a file that existed and could not be READ. The message describes what the compiler
     * concluded; the cause says why, and losing it makes a wrong conclusion unfalsifiable.
     *
     * @param message  what the compiler concluded
     * @param cause    what actually failed; may be null
     */
    public CompilerException(String message, Throwable cause) {
        super(true, message, cause);
    }

    @SuppressWarnings("unused")
    public CompilerException(Throwable cause) {
        super(cause);
    }
}
