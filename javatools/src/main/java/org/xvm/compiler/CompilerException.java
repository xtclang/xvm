package org.xvm.compiler;

import org.xvm.tool.Launcher.LauncherException;

/**
 * A non-fatal exception that can be emitted during any stage of the compilation
 * process to indicate that forward progress is not possible due to a flaw in
 * source material being compiled.
 */
public class CompilerException extends LauncherException {
    public CompilerException(String message) {
        super(true, message);
    }

    @SuppressWarnings("unused")
    public CompilerException(Throwable cause) {
        super(cause);
    }
}