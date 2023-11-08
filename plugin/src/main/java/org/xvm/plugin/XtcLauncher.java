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

        @Override
        public String toString() {
            return "{exitValue=" + exitValue + ", failure=" + error + "}";
        }
    }

    static String nativeLauncherFor(final XtcProjectDelegate delegate, final String mainClassName) {
        return switch (mainClassName) {
            case XtcCompileTask.XTC_COMPILER_CLASS_NAME -> "xtc";
            case XtcRunTask.XTC_RUNNER_CLASS_NAME -> "xec";
            default -> throw delegate.buildException("Unknown launcher for corresponding class: " + mainClassName);
        };
    }

    static XtcLauncher create(final XtcProjectDelegate delegate, final String mainClassName, final boolean isFork, final boolean isNativeLauncher) {
        if (isNativeLauncher) {
            assert isFork : "For option for native launcher will be ignored. A native process is always forked.";
            delegate.warn("{} The XTC plugin does not yet support using the native launcher.", delegate.prefix()); // TODO: Verify this works.
            return new NativeBinaryLauncher(delegate, nativeLauncherFor(delegate, mainClassName));
        }
        if (isFork) {
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
