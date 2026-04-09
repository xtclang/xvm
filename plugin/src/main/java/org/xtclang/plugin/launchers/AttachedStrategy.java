package org.xtclang.plugin.launchers;

import org.gradle.api.logging.Logger;

import org.xtclang.plugin.tasks.XtcLauncherTask;

/**
 * Attached (forked) execution strategy.
 * Launches in a separate JVM process with inherited I/O (stdout/stderr go to parent process).
 * Works for both compile and run tasks.
 */
public class AttachedStrategy<T extends XtcLauncherTask<?>> extends ForkedStrategy {

    public AttachedStrategy(final Logger logger, final String javaExecutable) {
        super(ExecutionMode.ATTACHED, logger, javaExecutable);
    }

    @Override
    protected StreamPlan configureIO(final ProcessBuilder pb, final XtcLauncherTask<?> task) {
        // NOTE: Don't use inheritIO() - it doesn't work when Gradle redirects file descriptors
        // Leave streams as PIPE (default) and manually copy them
        pb.redirectInput(ProcessBuilder.Redirect.INHERIT);

        boolean copyStdout = true;
        if (task.hasStdoutRedirect()) {
            final var stdoutFile = configuredRedirectFile(task, true);
            ensureParentDirectoryExists(stdoutFile);
            pb.redirectOutput(ProcessBuilder.Redirect.to(stdoutFile));
            logger.lifecycle("[plugin] Configured stdout redirect to: {}", stdoutFile.getAbsolutePath());
            copyStdout = false;
        }

        boolean copyStderr = true;
        if (task.hasStderrRedirect()) {
            final var stderrFile = configuredRedirectFile(task, false);
            ensureParentDirectoryExists(stderrFile);
            pb.redirectError(ProcessBuilder.Redirect.to(stderrFile));
            logger.lifecycle("[plugin] Configured stderr redirect to: {}", stderrFile.getAbsolutePath());
            copyStderr = false;
        }

        return new StreamPlan(copyStdout, copyStderr);
    }

    @Override
    protected String getDesc() {
        return "Invoking in forked JVM with inherited I/O (fork=true, detach=false)";
    }
}
