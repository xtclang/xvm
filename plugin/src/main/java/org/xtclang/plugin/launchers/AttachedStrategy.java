package org.xtclang.plugin.launchers;

import org.gradle.api.logging.Logger;

import org.xtclang.plugin.tasks.XtcLauncherTask;

/**
 * Attached (forked) execution strategy.
 * Launches in a separate JVM process with inherited I/O (stdout/stderr go to parent process).
 * Works for all launcher task types (compile, run, test, bundle).
 */
public class AttachedStrategy extends ForkedStrategy {

    public AttachedStrategy(final Logger logger, final String javaExecutable) {
        super(ExecutionMode.ATTACHED, logger, javaExecutable);
    }

    @Override
    protected boolean configureIO(final ProcessBuilder pb, final XtcLauncherTask<?> task) {
        // NOTE: Don't use inheritIO() - it doesn't work when Gradle redirects file descriptors
        // Leave streams as PIPE (default) and manually copy them
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);
        return true; // Signal that we need to manually copy stdout/stderr
    }

    @Override
    protected String getDesc() {
        return "Invoking in forked JVM with inherited I/O (fork=true, detach=false)";
    }
}
