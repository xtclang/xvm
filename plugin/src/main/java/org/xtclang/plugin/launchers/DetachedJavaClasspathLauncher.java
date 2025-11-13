package org.xtclang.plugin.launchers;

import org.gradle.api.logging.Logger;
import org.gradle.process.ExecResult;

import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.tasks.XtcLauncherTask;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A launcher that runs XTC tools in detached mode using javatools classes on the classpath.
 * The process continues running in the background after Gradle exits.
 *
 * <p>This launcher extends {@link JavaClasspathLauncher} and overrides the forked execution
 * to run the process in detached mode with output redirected to log files.
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Background execution: Process continues after Gradle exits</li>
 *   <li>Log file output: stdout/stderr redirected to timestamped log files</li>
 *   <li>PID reporting: Logs the process ID for management</li>
 * </ul>
 *
 * <p><b>Use Cases:</b>
 * <ul>
 *   <li>Long-running services (e.g., web servers, daemons)</li>
 *   <li>Development servers that should outlive the build</li>
 *   <li>Background compilation or processing tasks</li>
 * </ul>
 */
public class DetachedJavaClasspathLauncher<E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>>
        extends JavaClasspathLauncher<E, T> {

    /**
     * Creates a new DetachedJavaClasspathLauncher.
     * Automatically sets fork=true and detach=true.
     *
     * @param task The task being executed
     * @param logger Logger for diagnostic output
     * @param context Launcher execution context
     */
    public DetachedJavaClasspathLauncher(
            final T task,
            final Logger logger,
            final LauncherContext context) {
        // Force fork=true for detached mode
        super(task, logger, context, true);
    }

    /**
     * Overrides the forked execution to run in detached mode.
     * The process will run in the background with output redirected to log files.
     * This method is called within a try-catch block by the parent's invokeForked().
     */
    @Override
    protected ExecResult executeForkedProcess(final CommandLine cmd, final File javaToolsJar)
            throws IOException {
        logger.info("[plugin] Invoking {} in detached mode", cmd.getIdentifier());

        final List<String> command = buildForkedCommand(cmd, javaToolsJar);
        final ProcessBuilder processBuilder = new ProcessBuilder(command).directory(context.getWorkingDirectory());

        // Configure I/O redirection (use parent's method but with detached defaults)
        configureIoRedirection(processBuilder, cmd);

        logger.info("[plugin] Starting detached process: {}", String.join(" ", command));
        final Process process = processBuilder.start();
        final long pid = process.pid();

        logger.info("""
            [plugin] Detached XTC runner Started {} with PID: {}
            [plugin] Stop with: kill {} (unless there is a graceful way to exit)
            """.trim(), cmd.getIdentifier(), pid, pid);

        // Return success immediately without waiting
        return SimpleExecResult.success(0);
    }

    /**
     * Returns the default log file path for detached mode.
     * This is used when no custom stdout/stderr paths are specified in the DSL.
     *
     * @param cmd The command being executed
     * @return The default timestamped log file path
     */
    @Override
    protected String getDefaultStdoutPath(final CommandLine cmd) {
        final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return cmd.getIdentifier().toLowerCase() + "_pid_" + timestamp + ".log";
    }

    /**
     * Returns the default stderr path for detached mode.
     * By default, stderr goes to the same file as stdout.
     *
     * @param cmd The command being executed
     * @return The default stderr path (same as stdout for detached mode)
     */
    @Override
    protected String getDefaultStderrPath(final CommandLine cmd) {
        return getDefaultStdoutPath(cmd);
    }
}
