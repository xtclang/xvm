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

    @SuppressWarnings("unused")
    public CompilerException(Throwable cause) {
        super(cause);
    }
}
