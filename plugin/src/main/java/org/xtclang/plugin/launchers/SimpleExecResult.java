package org.xtclang.plugin.launchers;

import org.gradle.api.GradleException;
import org.gradle.process.ExecResult;

import static org.xtclang.plugin.XtcPluginUtils.failure;

/**
 * Simple implementation of Gradle's ExecResult interface.
 * Wraps exit codes and optional failure exceptions.
 */
final class SimpleExecResult implements ExecResult {
    private final int exitValue;
    private final Throwable cause;

    private SimpleExecResult(final int exitValue, final Throwable cause) {
        this.exitValue = exitValue;
        this.cause = cause;
    }

    /**
     * Creates a successful result with the given exit value.
     */
    static ExecResult ok(final int exitValue) {
        return new SimpleExecResult(exitValue, null);
    }

    /**
     * Creates a failed result with the given exit value and failure cause.
     */
    static ExecResult error(final int exitValue, final Throwable failure) {
        return new SimpleExecResult(exitValue, failure);
    }

    @Override
    public int getExitValue() {
        return exitValue;
    }

    @Override
    public ExecResult assertNormalExitValue() throws GradleException {
        if (exitValue != 0) {
            throw failure("Process exited with non-zero exit code: {}", exitValue);
        }
        return this;
    }

    @Override
    public ExecResult rethrowFailure() throws GradleException {
        if (cause != null) {
            throw failure(cause,"Process execution failed: {}", cause.getMessage());
        }
        return this;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ": [exitValue=" + exitValue + (cause != null ? ", ERROR=" + cause : ", SUCCESS") + ']';
    }
}