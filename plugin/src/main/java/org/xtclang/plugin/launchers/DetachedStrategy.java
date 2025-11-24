package org.xtclang.plugin.launchers;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.gradle.api.logging.Logger;

import org.xtclang.plugin.tasks.XtcLauncherTask;

import static java.time.format.DateTimeFormatter.ofPattern;
import static org.xtclang.plugin.XtcPluginUtils.failure;

/**
 * Detached (background) execution strategy.
 * Launches in a separate JVM process that continues after Gradle exits.
 * Supports stdout/stderr redirection with %TIMESTAMP% placeholder expansion.
 * Works for both compile and run tasks.
 */
public class DetachedStrategy<T extends XtcLauncherTask<?>> extends ForkedStrategy<T> {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = ofPattern("yyyyMMdd_HHmmss", Locale.ROOT);

    public DetachedStrategy(final Logger logger, final String javaExecutable) {
        super(logger, javaExecutable);
        logger.lifecycle("[plugin] [DetachedStrategy] javaExecutable: {}", javaExecutable);
    }

    @Override
    protected boolean configureIO(final ProcessBuilder pb, final XtcLauncherTask<?> task) {
        final var buildDir = task.getBuildDirectory().get().getAsFile();
        final String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        final String taskType = task.getClass().getSimpleName().toLowerCase(Locale.ROOT).replace("task", "");
        logger.lifecycle("[plugin] [DetachedStrategy] task: {}, taskType: {}", task.getName(), taskType);
        configureStream(pb, task, buildDir, timestamp, taskType, true);
        configureStream(pb, task, buildDir, timestamp, taskType, false);
        return false; // Streams are redirected to files, no need to manually copy
    }

    private void configureStream(
            final ProcessBuilder pb,
            final XtcLauncherTask<?> task,
            final File buildDir,
            final String timestamp,
            final String taskType,
            final boolean isStdout) {

        final boolean hasRedirect = isStdout ? task.hasStdoutRedirect() : task.hasStderrRedirect();
        final String streamName = isStdout ? "stdout" : "stderr";

        final File file;
        final String logMessage;

        if (hasRedirect) {
            final String configuredPath = isStdout ? task.getStdoutPath().get() : task.getStderrPath().get();
            final String expandedPath = expandTimestampPlaceholder(configuredPath, timestamp);
            // User-configured paths are relative to project directory
            file = new File(task.getProjectDirectory().get().getAsFile(), expandedPath);
            logMessage = "[plugin] Configured " + streamName + " redirect to: {}";
        } else {
            // Default paths go in build/xtc directory (build directory from layout)
            final File xtcDir = new File(buildDir, "xtc");
            file = new File(xtcDir, taskType + "_" + streamName + "_" + timestamp + ".log");
            logMessage = "[plugin] Using default " + streamName + " redirect: {}";
        }
        ensureParentDirectoryExists(file);
        final var redirect = ProcessBuilder.Redirect.to(file);
        if (isStdout) pb.redirectOutput(redirect); else pb.redirectError(redirect);
        logger.lifecycle(logMessage, file.getAbsolutePath());
    }

    @Override
    protected int waitForProcess(final Process process) {
        // For detached processes, log PID and don't wait - return success immediately
        final long pid = process.pid();
        logger.lifecycle("[plugin] Started detached process with PID: {}", pid);
        return 0;
    }

    @Override
    protected String getLogMessage() {
        return "Invoking in detached background process (fork=true, detach=true)";
    }

    private static String expandTimestampPlaceholder(final String path, final String timestamp) {
        return path.replace("%TIMESTAMP%", timestamp);
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
