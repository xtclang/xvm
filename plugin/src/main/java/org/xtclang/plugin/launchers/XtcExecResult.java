package org.xtclang.plugin.launchers;

import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class XtcExecResult implements ExecResult {
    private final int exitValue;
    private final Throwable failure;
    private final boolean logOutputs;
    private final String out;
    private final String err;

    XtcExecResult(final int exitValue, final Throwable failure, final boolean logOutputs, final String out, final String err) {
        this.exitValue = exitValue;
        this.failure = failure;
        this.logOutputs = logOutputs;
        // TODO: Remember to use the ExecResult.stdoutContents variables instead of the horrible stuff we do with byte arrays.
        this.out = out == null ? "" : out;
        this.err = err == null ? "" : err;
    }

    public static XtcExecResultBuilder builder(final Class<?> launcherClass, final CommandLine cmd, final boolean logOutput) {
        return new XtcExecResultBuilder(launcherClass, cmd, logOutput);
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

    public boolean hasOutputs() {
        return !out.isEmpty() || !err.isEmpty();
    }

    @Override
    public ExecResult assertNormalExitValue() throws ExecException {
        if (exitValue != 0) {
            throw new ExecException("XTC Launcher exited with non-zero exit code: " + exitValue, failure);
        }
        return this;
    }

    @Override
    public ExecResult rethrowFailure() throws ExecException {
        if (failure != null) {
            throw new ExecException("XTC Launcher exited with exception: " + failure.getMessage(), failure);
        }
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append(": [exitValue=").append(exitValue).append(", failure=").append(failure);
        if (logOutputs) {
            sb.append(", output.stdout=");
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
        }
        sb.append(']');
        return sb.toString();
    }

    /**
     * Extension of output stream that also flushes and echoes to console, as well as
     * the cached stream. TODO: Make configurable.
     */
    private static class XtcExecOutputStream extends OutputStream {
        private final OutputStream out;
        private final boolean alwaysFlush;

        @SuppressWarnings("unused")
        XtcExecOutputStream() {
            this(false);
        }

        XtcExecOutputStream(final boolean alwaysFlush) {
            this.out = new ByteArrayOutputStream();
            this.alwaysFlush = alwaysFlush;
        }

        @Override
        public void write(final int b) throws IOException {
            out.write(b);
            if (alwaysFlush) {
                out.flush();
            }
        }

        @Override
        public String toString() {
            return out.toString();
        }
    }

    /**
     * Subclass to a Gradle Exec Result.
     */
    public static final class XtcExecResultBuilder {
        private final Class<?> launcherClass;
        private final CommandLine cmd;
        private final XtcExecOutputStream out;
        private final XtcExecOutputStream err;

        private int exitValue;
        private boolean hasExitValue;
        private Throwable failure;
        private boolean logOutputs;

        private XtcExecResultBuilder(final Class<?> launcherClass, final CommandLine cmd, final boolean logOutputs) {
            this.launcherClass = launcherClass;
            this.cmd = cmd;
            this.hasExitValue = false; // has exit value been set?
            this.logOutputs = logOutputs;
            this.out = new XtcExecOutputStream(logOutputs);
            this.err = new XtcExecOutputStream(logOutputs);
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
            return cmd;
        }

        OutputStream getOut() {
            return out;
        }

        OutputStream getErr() {
            return err;
        }

        @SuppressWarnings("UnusedReturnValue")
        XtcExecResultBuilder exitValue(final int exitValue) {
            this.exitValue = exitValue;
            this.hasExitValue = true;
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

        XtcExecResultBuilder logOutputs(final boolean logOutputs) {
            this.logOutputs = logOutputs;
            return this;
        }

        private static String outputAsString(final XtcExecOutputStream out) {
            return out.toString();
        }

        XtcExecResult build() {
            return new XtcExecResult(exitValue, failure, logOutputs, outputAsString(out), outputAsString(err));
        }
    }
}
