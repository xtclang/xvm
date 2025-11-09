package org.xtclang.plugin.launchers;

import org.gradle.api.logging.Logger;
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
        return String.format("[plugin] (launcher='%s', task='%s').",
                getClass().getSimpleName(), taskName);
    }

    @SuppressWarnings("UnusedReturnValue")
    protected boolean validateCommandLine(final CommandLine cmd) {
        return true;
    }
}
