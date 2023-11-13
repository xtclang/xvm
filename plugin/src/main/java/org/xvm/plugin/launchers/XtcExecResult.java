package org.xvm.plugin.launchers;

import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecException;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

public final class XtcExecResult implements ExecResult {
    private final int exitValue;
    private final Throwable failure;
    private final String out;
    private final String err;

    XtcExecResult(final int exitValue, final Throwable failure, final OutputStream out, final OutputStream err) {
        this.exitValue = exitValue;
        this.failure = failure;
        this.out = out == null ? "" : out.toString();
        this.err = err == null ? "" : err.toString();
    }

    public static XtcExecResultBuilder builder(final Class<? extends XtcLauncher> launcherClass, final CommandLine args) {
        return new XtcExecResultBuilder(launcherClass, args);
    }

    @SuppressWarnings("unused")
    public boolean isSuccessful() {
        return exitValue == 0;
    }

    @SuppressWarnings("unused")
    public String getOutputStdout() {
        return out;
    }

    @SuppressWarnings("unused")
    public String getOutputStderr() {
        return err;
    }

    @SuppressWarnings("unused")
    public Throwable getFailure() {
        return failure;
    }

    @Override
    public int getExitValue() {
        return exitValue;
    }

    boolean hasOutputs() {
        return !out.isEmpty() || !err.isEmpty();
    }

    @Override
    public ExecResult assertNormalExitValue() throws ExecException {
        if (exitValue != 0) {
            throw new ExecException("XTC exited with non-zero exit code: " + exitValue, failure);
        }
        return this;
    }

    @Override
    public ExecResult rethrowFailure() throws ExecException {
        if (failure != null) {
            throw new ExecException("XTC exited with exception: " + failure.getMessage(), failure);
        }
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append(": [exitValue=").append(exitValue).append(", failure=").append(failure).append(", output.stdout=");
        if (out.isEmpty()) {
            sb.append("[empty]");
        } else {
            sb.append("[").append(out.lines().count()).append(" lines]");
        }
        sb.append(", output.stderr=");
        if (err.isEmpty()) {
            sb.append("[empty]");
        } else {
            sb.append("[").append(err.lines().count()).append(" lines]");
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Subclass to a Gradle Exec Result.
     */
    public static class XtcExecResultBuilder {
        private final Class<? extends XtcLauncher> launcherClass;
        private final CommandLine args;

        private int exitValue;
        private boolean hasExitValue;
        private Throwable failure;
        private OutputStream out;
        private OutputStream err;

        private XtcExecResultBuilder(final Class<? extends XtcLauncher> launcherClass, final CommandLine args) {
            this.launcherClass = launcherClass;
            this.args = args;
            this.hasExitValue = false; // has exit value been set?
            out(new ByteArrayOutputStream());
            err(new ByteArrayOutputStream());
        }

        boolean hasExitValue() {
            return hasExitValue;
        }

        @SuppressWarnings("unused")
        boolean isSuccessful() {
            return hasExitValue && exitValue == 0;
        }

        @SuppressWarnings("unused")
        CommandLine getCommandLine() {
            return args;
        }

        OutputStream getOut() {
            return out;
        }

        OutputStream getErr() {
            return out;
        }

        @SuppressWarnings("UnusedReturnValue")
        XtcExecResultBuilder out(final OutputStream out) {
            this.out = out;
            return this;
        }

        @SuppressWarnings("UnusedReturnValue")
        XtcExecResultBuilder err(final OutputStream err) {
            this.err = err;
            return this;
        }

        XtcExecResultBuilder exitValue(final int exitValue) {
            this.exitValue = exitValue;
            this.hasExitValue = true;
            // If we have a wrapper exec result, add it?
            return this;
        }

        @SuppressWarnings("UnusedReturnValue")
        XtcExecResultBuilder failure(final Throwable failure) {
            this.failure = new ExecException(launcherClass.getSimpleName() + ' ' + failure.getMessage(), failure);
            return this;
        }

        @SuppressWarnings("UnusedReturnValue")
        XtcExecResultBuilder execResult(final ExecResult execResult) {
            exitValue(execResult.getExitValue());
            try {
                execResult.rethrowFailure();
                execResult.assertNormalExitValue();
            } catch (final ExecException e) {
                failure(e);
            }
            return this;
        }

        XtcExecResult build() {
            return new XtcExecResult(exitValue, failure, out, err);
        }
    }
}
