package org.xtclang.plugin.launchers;

import static org.xtclang.plugin.launchers.XtcExecResult.XtcExecResultBuilder;

import java.util.function.Function;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.process.BaseExecSpec;
import org.gradle.process.ExecResult;

import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.tasks.XtcLauncherTask;

public abstract class XtcLauncher<E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>> implements Function<CommandLine, ExecResult> {
    protected final LauncherConfiguration config;
    protected final T task;
    protected final String taskName;
    protected final Logger logger;
    protected final String prefix;

    protected XtcLauncher(final LauncherConfiguration config, final T task) {
        this.config = config;
        this.task = task;
        this.taskName = task.getName();
        this.logger = config.getLogger();
        this.prefix = config.getLogPrefix();
    }

    @Override
    public String toString() {
        return String.format("%s (launcher='%s', task='%s', fork=%s, native=%s).",
                prefix, getClass().getSimpleName(), taskName, shouldFork(), isNativeLauncher());
    }

    @Override
    public abstract ExecResult apply(CommandLine cmd);

    protected boolean shouldFork() {
        return task.getFork().get();
    }

    protected boolean isNativeLauncher() {
        return task.getUseNativeLauncher().get();
    }

    protected void redirectIo(final XtcExecResultBuilder builder, final BaseExecSpec spec) {
        // TODO, simplify, just send a stream setter for the various streams or our own class based on the existing ExecResult.contentsOfOutput* or something.
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

    protected static XtcExecResult createExecResult(final XtcExecResultBuilder builder) {
        assert builder.hasExitValue();
        // TODO: System.exit callback if we are running in the builder thread, or things get nasty.
        final var result = builder.build();
        assert result.isSuccessful() || result.getFailure() != null : "Should always have a failure for an XtcExecResult";
        return result;
    }

    protected final XtcExecResultBuilder resultBuilder(final CommandLine cmd) {
        return XtcExecResult.builder(getClass(), cmd);
    }

    @SuppressWarnings("UnusedReturnValue")
    protected boolean validateCommandLine(final CommandLine cmd) {
        return true;
    }

    protected final GradleException buildException(final String msg, final Object... args) {
        return buildException(null, msg, args);
    }

    protected final GradleException buildException(final Throwable t, final String msg, final Object... args) {
        String formattedMsg = String.format(msg.replace("{}", "%s"), args);
        logger.error(formattedMsg, t);
        return new GradleException(prefix + ": " + formattedMsg, t);
    }

    public boolean hasVerboseLogging() {
        return config.isVerboseLogging();
    }
}
