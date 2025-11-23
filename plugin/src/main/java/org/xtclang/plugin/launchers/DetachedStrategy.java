package org.xtclang.plugin.launchers;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.gradle.api.logging.Logger;

import org.xtclang.plugin.tasks.XtcLauncherTask;

import static org.xtclang.plugin.XtcPluginUtils.failure;

/**
 * Detached (background) execution strategy.
 * Launches in a separate JVM process that continues after Gradle exits.
 * Supports stdout/stderr redirection with %TIMESTAMP% placeholder expansion.
 * Works for both compile and run tasks.
 */
public class DetachedStrategy<T extends XtcLauncherTask<?>> extends ForkedStrategy<T> {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    public DetachedStrategy(final Logger logger, final String javaExecutable) {
        super(logger, javaExecutable);
    }

    @Override
    protected boolean configureIO(final ProcessBuilder pb, final XtcLauncherTask<?> task) throws IOException {
        final var projectDir = task.getProjectDirectory().get().getAsFile();
        final String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);

        // Configure stdout redirect
        if (task.hasStdoutRedirect()) {
            final String stdoutPath = expandTimestampPlaceholder(task.getStdoutPath().get(), timestamp);
            final File stdoutFile = new File(projectDir, stdoutPath);
            ensureParentDirectoryExists(stdoutFile);
            pb.redirectOutput(ProcessBuilder.Redirect.to(stdoutFile));
            logger.info("[plugin] Configured stdout redirect to: {}", stdoutFile.getAbsolutePath());
        } else {
            // Default stdout for detached process
            final String taskType = task.getClass().getSimpleName().toLowerCase().replace("task", "");
            final String defaultStdout = "build/xtc/" + taskType + "_stdout_" + timestamp + ".log";
            final File stdoutFile = new File(projectDir, defaultStdout);
            ensureParentDirectoryExists(stdoutFile);
            pb.redirectOutput(ProcessBuilder.Redirect.to(stdoutFile));
            logger.info("[plugin] Using default stdout redirect: {}", stdoutFile.getAbsolutePath());
        }

        // Configure stderr redirect
        if (task.hasStderrRedirect()) {
            final String stderrPath = expandTimestampPlaceholder(task.getStderrPath().get(), timestamp);
            final File stderrFile = new File(projectDir, stderrPath);
            ensureParentDirectoryExists(stderrFile);
            pb.redirectError(ProcessBuilder.Redirect.to(stderrFile));
            logger.info("[plugin] Configured stderr redirect to: {}", stderrFile.getAbsolutePath());
        } else {
            // Default stderr for detached process
            final String taskType = task.getClass().getSimpleName().toLowerCase().replace("task", "");
            final String defaultStderr = "build/xtc/" + taskType + "_stderr_" + timestamp + ".log";
            final File stderrFile = new File(projectDir, defaultStderr);
            ensureParentDirectoryExists(stderrFile);
            pb.redirectError(ProcessBuilder.Redirect.to(stderrFile));
            logger.info("[plugin] Using default stderr redirect: {}", stderrFile.getAbsolutePath());
        }

        // Log redirect info after process starts
        if (task.hasStdoutRedirect()) {
            logger.lifecycle("[plugin] stdout redirected to: {}", pb.redirectOutput().file());
        }
        if (task.hasStderrRedirect()) {
            logger.lifecycle("[plugin] stderr redirected to: {}", pb.redirectError().file());
        }

        return false; // Streams are redirected to files, no need to manually copy
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

    private void ensureParentDirectoryExists(final File file) throws IOException {
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
