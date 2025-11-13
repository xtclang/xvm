package org.xtclang.plugin.launchers;

import org.gradle.api.GradleException;
import org.gradle.process.ExecResult;

/**
 * Simple implementation of Gradle's ExecResult interface.
 * Wraps exit codes and optional failure exceptions.
 */
final class SimpleExecResult implements ExecResult {
    private final int exitValue;
    private final Throwable failure;

    private SimpleExecResult(final int exitValue, final Throwable failure) {
        this.exitValue = exitValue;
        this.failure = failure;
    }

    /**
     * Creates a successful result with the given exit value.
     */
    static ExecResult success(final int exitValue) {
        return new SimpleExecResult(exitValue, null);
    }

    /**
     * Creates a failed result with the given exit value and failure cause.
     */
    static ExecResult failure(final int exitValue, final Throwable failure) {
        return new SimpleExecResult(exitValue, failure);
    }

    @Override
    public int getExitValue() {
        return exitValue;
    }

    @Override
    public ExecResult assertNormalExitValue() throws GradleException {
        if (exitValue != 0) {
            throw new GradleException("Process exited with non-zero exit code: " + exitValue, failure);
        }
        return this;
    }

    @Override
    public ExecResult rethrowFailure() throws GradleException {
        if (failure != null) {
            throw new GradleException("Process execution failed: " + failure.getMessage(), failure);
        }
        return this;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ": [exitValue=" + exitValue +
            (failure != null ? ", failure=" + failure : ", SUCCESS") + ']';
    }
}