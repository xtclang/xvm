package org.xvm.plugin;

import org.gradle.api.Project;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecException;

public abstract class XtcLauncher extends ProjectDelegate<CommandLine, ExecResult> {

    static class XtcExecResult implements ExecResult {
        static final XtcExecResult OK = new XtcExecResult(0, null);

        private final int exitValue;
        private final Throwable error;

        XtcExecResult(final int exitValue, final Throwable error) {
            this.exitValue = exitValue;
            this.error = error;
        }

        @Override
        public int getExitValue() {
            return exitValue;
        }

        @Override
        public ExecResult assertNormalExitValue() throws ExecException {
            if (exitValue != 0) {
                throw new ExecException("XTC exited with non-zero exit code: " + exitValue, error);
            }
            return this;
        }

        @Override
        public ExecResult rethrowFailure() throws ExecException {
            if (error != null) {
                throw new ExecException("XTC exited with exception: " + error.getMessage(), error);
            }
            return this;
        }
    }

    static XtcLauncher create(final XtcProjectDelegate delegate, final String mainClassName, final boolean fork) {
        if (fork) {
            return new JavaExecLauncher(delegate);
        }
        return new BuildThreadLauncher(delegate, mainClassName);
    }

    protected XtcLauncher(final Project project) {
        super(project);
    }

    protected void showOutput(final CommandLine args, final String out, final String err) {
        // TODO: Don't always redirect and reprint outputs. Make it configurable.
        if (!out.isEmpty()) {
            lifecycle("{} '{}' JavaExec stdout:", prefix, args.getMainClassName());
            System.out.println(out);
        }
        if (!err.isEmpty()) {
            lifecycle("{} '{}' JavaExec stderr:", prefix, args.getMainClassName());
            System.err.println(err);
        }
    }
}
