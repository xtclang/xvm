package org.xtclang.plugin.launchers;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.process.ExecResult;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.tasks.XtcLauncherTask;
import org.xtclang.plugin.launchers.XtcExecResult.XtcExecResultBuilder;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

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
     * @param toolLauncher Type-safe reference to the tool's launch method (e.g., Runner::launch)
     * @param projectVersion The project version for artifact resolution
     * @param xdkFileTree The XDK file tree for resolving javatools
     * @param javaToolsConfig The javatools configuration
     * @param toolchainExecutable The Java toolchain executable path
     * @param workingDirectory The working directory for process execution
     */
    public DetachedJavaClasspathLauncher(
            final T task,
            final Logger logger,
            final Consumer<String[]> toolLauncher,
            final Provider<@NotNull String> projectVersion,
            final Provider<@NotNull FileTree> xdkFileTree,
            final Provider<@NotNull FileCollection> javaToolsConfig,
            final Provider<@NotNull String> toolchainExecutable,
            final File workingDirectory) {
        // Force fork=true for detached mode
        super(task, logger, toolLauncher, projectVersion, xdkFileTree, javaToolsConfig, toolchainExecutable, workingDirectory, true);
    }

    /**
     * Overrides the forked execution to run in detached mode.
     * The process will run in the background with output redirected to log files.
     * This method is called within a try-catch block by the parent's invokeForked().
     */
    @Override
    protected ExecResult executeForkedProcess(final CommandLine cmd, final File javaToolsJar, final XtcExecResultBuilder builder)
            throws IOException {
        logger.lifecycle("[plugin] Invoking {} in detached mode", cmd.getIdentifier());

        final List<String> command = buildForkedCommand(cmd, javaToolsJar);

        final ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDirectory);

        // Detach mode: redirect output to log files and don't wait for completion
        final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        final String logFileName = cmd.getIdentifier().toLowerCase() + "_pid_" + timestamp + ".log";
        final File logFile = new File(workingDirectory, logFileName);

        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
        processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(logFile));

        logger.info("[plugin] Starting detached process: {}", String.join(" ", command));
        final Process process = processBuilder.start();
        final long pid = process.pid();

        logger.lifecycle("""
            [plugin] Started {} with PID: {}
            [plugin] Stop with: kill {} (unless there is a graceful way to exit)
            [plugin] Logs: {}
            """.trim(), cmd.getIdentifier(), pid, pid, logFile.getAbsolutePath());

        // Return success immediately without waiting
        builder.exitValue(0);
        return createExecResult(builder);
    }
}
