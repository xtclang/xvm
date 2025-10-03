package org.xtclang.plugin.launchers;

import static org.xtclang.plugin.launchers.XtcExecResult.XtcExecResultBuilder;

import org.gradle.api.logging.Logger;
import org.gradle.process.BaseExecSpec;
import org.gradle.process.ExecResult;

import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.tasks.XtcLauncherTask;

public abstract class XtcLauncher<E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>> {
    protected final T task;
    protected final String taskName;
    protected final Logger logger;

    protected XtcLauncher(final T task, final Logger logger) {
        this.task = task;
        this.taskName = task.getName();
        this.logger = logger;
    }
    
    // Abstract method that subclasses must implement
    public abstract ExecResult apply(final CommandLine cmd);

    @Override
    public String toString() {
        return String.format("[plugin] (launcher='%s', task='%s', fork=%s, native=%s).",
                getClass().getSimpleName(), taskName, shouldFork(), isNativeLauncher());
    }

    protected boolean shouldFork() {
        return task.getFork().get();
    }

    protected boolean isNativeLauncher() {
        return task.getUseNativeLauncher().get();
    }

    protected void redirectIo(final BaseExecSpec spec) {
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
}
