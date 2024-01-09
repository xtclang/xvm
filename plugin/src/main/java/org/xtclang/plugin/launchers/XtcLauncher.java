package org.xtclang.plugin.launchers;

import org.gradle.api.Project;
import org.gradle.api.logging.LogLevel;
import org.gradle.process.BaseExecSpec;
import org.gradle.process.ExecResult;
import org.xtclang.plugin.ProjectDelegate;
import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.tasks.XtcLauncherTask;

import static org.gradle.api.logging.LogLevel.ERROR;
import static org.gradle.api.logging.LogLevel.LIFECYCLE;
import static org.xtclang.plugin.launchers.XtcExecResult.XtcExecResultBuilder;

public abstract class XtcLauncher<E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>> extends ProjectDelegate<CommandLine, ExecResult> {
    protected final T task;

    protected XtcLauncher(final Project project, final T task) {
        super(project);
        this.task = task;
    }

    @Override
    public String toString() {
        return String.format("%s (launcher='%s', task='%s', fork=%s, native=%s).", prefix, getClass().getSimpleName(), task.getName(), isFork(), isNativeLauncher());
    }

    protected boolean shouldRedirectOutputToLogger() {
        return task.getLogOutputs().get();
    }

    protected boolean isFork() {
        return task.getFork().get();
    }

    protected boolean isNativeLauncher() {
        return task.getUseNativeLauncher().get();
    }

    protected void redirectIo(final XtcExecResultBuilder builder, final BaseExecSpec spec) {
        if (shouldRedirectOutputToLogger()) {
            spec.setStandardOutput(builder.getOut());
            spec.setErrorOutput(builder.getErr());
            if (task.hasOutputRedirects()) {
                warn("{} WARNING: Task '{}' is already configured to override stdout and/or stderr. It may cause problems to redirect them to the build log.", prefix, task.getName());
            }
        }

        // TODO, simplify, just send a stream setter for the various streams or our own class based on the existin ExecResult.contentsOfOutput* or something.
        if (task.hasStdinRedirect()) {
            spec.setStandardInput(task.getStdin().get());
        }
        if (task.hasStdoutRedirect()) {
            spec.setStandardOutput(task.getStdout().get());
        }
        if (task.hasStderrRedirect()) {
            spec.setErrorOutput(task.getStderr().get());
        }
    }

    protected XtcExecResult createExecResult(final XtcExecResultBuilder builder) {
        assert builder.hasExitValue();
        // TODO: System.exit callback if we are running in the builder thread, or things get nasty.
        final var result = builder.build();
        assert result.isSuccessful() || result.getFailure() != null : "Should always have a failure for an XtcExecResult";
        return logOutputs(result);
    }

    protected final XtcExecResultBuilder resultBuilder(final CommandLine cmd) {
        return XtcExecResult.builder(getClass(), cmd, task.getLogOutputs().get());
    }

    @SuppressWarnings("UnusedReturnValue")
    protected boolean validateCommandLine(final CommandLine cmd) {
        return true;
    }

    public XtcExecResult logOutputs(final XtcExecResult result) {
        final var hasOutputs = result.hasOutputs();

        if (shouldRedirectOutputToLogger() && hasOutputs) {
            logger.info("{} Task '{}' Exec output was not captured in logger. Check your scroll back buffer or redirected stream sinks for any task output.", prefix, task.getName());
            return result;
        }

        if (hasOutputs) {
            logStream("stdout", LIFECYCLE, result.getOutputStdout());
            logStream("stderr", ERROR, result.getOutputStderr());
        } else {
            logger.info("{} [stdout | stderr] Task '{}' did not generate any output or errors.", prefix, task.getName());
        }
        return result;
    }

    public void logStream(final String name, final LogLevel level, final String output) {
        if (!output.isEmpty()) {
            output.lines().forEach(line -> logger.log(level, "{} [{}] @ {}", prefix, name, line));
        }
    }
}
