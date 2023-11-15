package org.xvm.plugin.launchers;

import org.gradle.api.Project;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecException;
import org.xvm.plugin.ProjectDelegate;
import org.xvm.plugin.XtcProjectDelegate;

import java.util.function.Consumer;

import static org.xvm.plugin.tasks.XtcCompileTask.XTC_COMPILER_CLASS_NAME;
import static org.xvm.plugin.tasks.XtcRunTask.XTC_RUNNER_CLASS_NAME;

public abstract class XtcLauncher extends ProjectDelegate<CommandLine, ExecResult> {

    /**
     * Subclass to a Gradle Exec Result.
     */
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

    protected XtcLauncher(final Project project) {
        super(project);
    }

    static String nativeLauncherFor(final XtcProjectDelegate delegate, final String mainClassName) {
        return switch (mainClassName) {
            case XTC_COMPILER_CLASS_NAME -> "xtc";
            case XTC_RUNNER_CLASS_NAME -> "xec";
            default -> throw delegate.buildException("Unknown launcher for corresponding class: " + mainClassName);
        };
    }

    public static XtcLauncher create(final XtcProjectDelegate delegate, final String mainClassName, final boolean isFork, final boolean isNativeLauncher) {
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

    protected void showOutput(final CommandLine args, final String output, final Consumer<String> printer) {
        if (!output.isEmpty()) {
            lifecycle("{} '{}' XTC stdout ({} lines):", prefix, args.getMainClassName(), output.lines().count());
            printer.accept(output);
        }
    }

    protected void showOutput(final CommandLine args, final String out, final String err) {
        // TODO: Don't always redirect and reprint outputs. Make it configurable.
        showOutput(args, out, System.out::println);
        showOutput(args, err, System.err::println);
    }
}
