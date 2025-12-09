package org.xtclang.plugin.launchers;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Locale;

import org.gradle.api.logging.Logger;

import org.xtclang.plugin.tasks.XtcLauncherTask;

import static org.xtclang.plugin.XtcPluginUtils.TIMESTAMP_FORMAT;
import static org.xtclang.plugin.XtcPluginUtils.expandTimestampPlaceholder;
import static org.xtclang.plugin.XtcPluginUtils.failure;

/**
 * Detached (background) execution strategy.
 * Launches in a separate JVM process that continues after Gradle exits.
 * Supports stdout/stderr redirection with %TIMESTAMP% placeholder expansion.
 * Works for both compile and run tasks.
 */
public class DetachedStrategy<T extends XtcLauncherTask<?>> extends ForkedStrategy {

    public DetachedStrategy(final Logger logger, final String javaExecutable) {
        super(ExecutionMode.DETACHED, logger, javaExecutable);
    }

    @Override
    protected boolean configureIO(final ProcessBuilder pb, final XtcLauncherTask<?> task) {
        final var buildDir = task.getBuildDirectory().get().getAsFile();
        final String taskType = task.getClass().getSimpleName().toLowerCase(Locale.ROOT).replace("task", "");
        logger.lifecycle("[plugin] [DetachedStrategy] task: {}, taskType: {}", task.getName(), taskType);
        configureStream(pb, task, buildDir, taskType, true);
        configureStream(pb, task, buildDir, taskType, false);
        return false; // Streams are redirected to files, no need to manually copy
    }

    private void configureStream(
            final ProcessBuilder pb,
            final XtcLauncherTask<?> task,
            final File buildDir,
            final String taskType,
            final boolean isStdout) {

        final boolean hasRedirect = isStdout ? task.hasStdoutRedirect() : task.hasStderrRedirect();
        final String streamName = isStdout ? "stdout" : "stderr";

        final File file;
        final String msg;

        if (hasRedirect) {
            final String configuredPath = expandTimestampPlaceholder(isStdout ? task.getStdoutPath().get() : task.getStderrPath().get());
            // User-configured paths are relative to project directory
            file = new File(task.getProjectDirectory().get().getAsFile(), configuredPath);
            msg = "[plugin] Configured " + streamName + " redirect to: {}";
        } else {
            // Default paths go in build/xtc directory (build directory from layout)
            final File xtcDir = new File(buildDir, "xtc");
            final String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            file = new File(xtcDir, taskType + "_" + streamName + "_" + timestamp + ".log");
            msg = "[plugin] Using default " + streamName + " redirect: {}";
        }
        ensureParentDirectoryExists(file);
        final var redirect = ProcessBuilder.Redirect.to(file);
        if (isStdout) pb.redirectOutput(redirect); else pb.redirectError(redirect);
        logger.lifecycle(msg, file.getAbsolutePath());
    }

    @Override
    protected int waitForProcess(final Process process) {
        // For detached processes, log PID and don't wait - return success immediately
        final long pid = process.pid();
        logger.lifecycle("[plugin] Started detached process with PID: {}", pid);
        return 0;
    }

    @Override
    protected String getDesc() {
        return "Invoking in detached background process (fork=true, detach=true)";
    }

    private void ensureParentDirectoryExists(final File file) {
        final File parentDir = file.getParentFile();
        if (parentDir == null) {
            logger.error("[plugin] Parent directory does not exist: {}", file.getAbsolutePath());
        }
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw failure("ensureParentDirectoryExists; failed to create directory: {}", parentDir.getAbsolutePath());
            }
        }
    }
}
