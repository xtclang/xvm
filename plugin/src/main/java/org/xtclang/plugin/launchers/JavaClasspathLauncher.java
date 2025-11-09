package org.xtclang.plugin.launchers;

import static org.xtclang.plugin.XtcJavaToolsRuntime.resolveJavaTools;
import static org.xtclang.plugin.XtcJavaToolsRuntime.withJavaTools;
import static org.xtclang.plugin.XtcPluginConstants.XDK_JAVATOOLS_NAME_JAR;
import static org.xtclang.plugin.XtcPluginUtils.expandTimestampPlaceholder;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.process.ExecResult;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.tasks.XtcLauncherTask;

import org.xvm.tool.Launcher.LauncherException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A launcher that directly invokes XTC tools using javatools classes on the classpath.
 * This eliminates the need for external process execution and reflection-based invocation
 * by leveraging compile-time type information from the compileOnly javatools dependency.
 *
 * <p><b>Key Features:</b>
 * <ul>
 *   <li>Direct invocation: No process spawning overhead</li>
 *   <li>Type-safe: Uses actual javatools classes, not reflection</li>
 *   <li>Error capture: Can attach custom ErrorListener implementations</li>
 *   <li>Optional forking: Can still fork to a separate process if needed</li>
 * </ul>
 *
 * <p><b>Performance Benefits:</b>
 * <ul>
 *   <li>No JVM startup overhead</li>
 *   <li>Shared classloader benefits</li>
 *   <li>Direct error reporting via ErrorListener</li>
 * </ul>
 */
public class JavaClasspathLauncher<E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>> extends XtcLauncher<E, T> {
    protected final LauncherContext context;
    protected final Consumer<String[]> toolLauncher;
    private final boolean fork;

    /**
     * Creates a new JavaClasspathLauncher.
     *
     * @param task The task being executed
     * @param logger Logger for diagnostic output
     * @param toolLauncher Type-safe reference to the tool's launch method (e.g., Compiler::launch)
     * @param context Launcher execution context (project version, XDK, javatools, etc.)
     * @param fork Whether to fork to a separate process
     */
    public JavaClasspathLauncher(
            final T task,
            final Logger logger,
            final Consumer<String[]> toolLauncher,
            final LauncherContext context,
            final boolean fork) {
        super(task, logger);
        this.toolLauncher = toolLauncher;
        this.context = context;
        this.fork = fork;
    }

    @Override
    public ExecResult apply(final CommandLine cmd) {
        logger.info("[plugin] Launching task (project version: {}), with JavaClasspathLauncher: {}", context.getProjectVersion(), this);
        final var javaToolsJar = resolveJavaTools(context.getProjectVersion(), context.getJavaToolsConfig(), context.getXdkFileTree(), logger);
        logLaunchInfo(cmd, javaToolsJar);
        return fork ? invokeForked(cmd, javaToolsJar) : invokeDirectly(cmd, javaToolsJar);
    }

    /**
     * Logs detailed information about the launch command.
     *
     * @param cmd The command line to execute
     * @param javaToolsJar The javatools JAR file being used
     */
    private void logLaunchInfo(final CommandLine cmd, final File javaToolsJar) {
        logger.info("[plugin] {} (launcher: {}); Using '{}' in classpath from: {}",
                cmd.getIdentifier(), getClass().getSimpleName(), XDK_JAVATOOLS_NAME_JAR, javaToolsJar);

        if (task.hasVerboseLogging()) {
            final var launchLine = cmd.toString(javaToolsJar);
            logger.lifecycle("[plugin] JavaClasspath command (launcher {}, fork={}): {}",
                    getClass().getSimpleName(), fork, launchLine);
        }

        // ALWAYS log the exact execution details
        final var argsBuilder = new StringBuilder();
        int idx = 0;
        for (final String arg : cmd.toList()) {
            argsBuilder.append(String.format("[plugin]   [%d] = '%s'%n", idx++, arg));
        }

        var infoLog = """
                [plugin] Fork mode: %s
                [plugin] Main class: %s
                [plugin] JVM args: %s
                [plugin] Classpath: %s
                [plugin] Working directory: %s
                [plugin] Program arguments (%d total):
                %s[plugin] ===== END EXECUTION DETAILS =====""".formatted(
                fork,
                cmd.getMainClassName(),
                cmd.getJvmArgs(),
                javaToolsJar.getAbsolutePath(),
                context.getWorkingDirectory().getAbsolutePath(),
                cmd.toList().size(),
                argsBuilder.toString());
        logger.info(infoLog);
    }

    /**
     * Invokes the compiler directly in the current thread using javatools classes.
     * This is the high-performance path that avoids process spawning.
     *
     * <p>The key insight: We have compile-time access to javatools types (via compileOnly),
     * but at runtime those classes must be loaded from javatools.jar. This method:
     * <ol>
     *   <li>Creates a URLClassLoader with javatools.jar</li>
     *   <li>Sets it as the thread context classloader</li>
     *   <li>Invokes the compiler, which loads from that classloader</li>
     *   <li>Restores the original classloader</li>
     * </ol>
     *
     * <p>This works in both scenarios:
     * <ul>
     *   <li>XDK development: javatools.jar from local build</li>
     *   <li>Third-party (xtc-app-template): javatools.jar from extracted distribution</li>
     * </ul>
     */
    private ExecResult invokeDirectly(final CommandLine cmd, final File javaToolsJar) {
        logger.lifecycle("[plugin] Invoking {} directly in current thread (no fork)", cmd.getIdentifier());

        try {
            // Convert relative paths to absolute paths for in-thread execution
            // (Setting user.dir doesn't affect File resolution in an already-running JVM)
            final CommandLine absoluteCmd = convertToAbsolutePaths(cmd);

            // Use the utility to execute with javatools on classpath
            // Inside withJavaTools(), the context classloader is set so we can call javatools DIRECTLY!
            return withJavaTools(javaToolsJar, logger, () -> {
                // Convert command line to string array
                final String[] args = absoluteCmd.toList().toArray(new String[0]);
                try {
                    toolLauncher.accept(args);

                    // If we get here, the tool succeeded
                    logger.info("[plugin] Tool execution completed successfully");
                    return SimpleExecResult.success(0);

                } catch (final LauncherException e) {
                    // Expected exit mechanism from launcher - this works because we're in the
                    // same classloader context thanks to withJavaTools()
                    final int exitCode = e.error ? 1 : 0;
                    if (exitCode != 0) {
                        // IMPORTANT: We must extract the exception information as pure strings before
                        // creating the GradleException. Gradle's configuration cache cannot serialize
                        // LauncherException (from javatools classloader) because it may contain
                        // references to non-serializable javatools types.
                        final String errorMessage = buildErrorMessage(e);
                        final GradleException gradleException = new GradleException("XTC tool execution failed: " + errorMessage);
                        logger.error("[plugin] Tool execution failed with exit code: {}", exitCode);
                        return SimpleExecResult.failure(exitCode, gradleException);
                    } else {
                        logger.info("[plugin] Tool execution completed with exit code: {}", exitCode);
                        return SimpleExecResult.success(exitCode);
                    }
                }
            });

        } catch (final Exception e) {
            logger.error("[plugin] Direct invocation failed with exception", e);
            return SimpleExecResult.failure(-1, e);
        }
    }

    /**
     * Extracts error message and cause chain from LauncherException.
     * This is necessary because LauncherException is loaded from javatools classloader
     * and cannot be serialized by Gradle's configuration cache. We extract all information
     * as strings instead.
     *
     * @param e the LauncherException from javatools classloader
     * @return a string containing the full error message and cause chain
     */
    private String buildErrorMessage(final LauncherException e) {
        final StringBuilder message = new StringBuilder();

        // Start with the LauncherException message if present
        if (e.getMessage() != null && !e.getMessage().isEmpty()) {
            message.append(e.getMessage());
        } else {
            message.append("Execution failed");
        }

        // Walk the cause chain and append each cause's message
        Throwable cause = e.getCause();
        while (cause != null) {
            message.append("\nCaused by: ");
            message.append(cause.getClass().getSimpleName());
            if (cause.getMessage() != null && !cause.getMessage().isEmpty()) {
                message.append(": ").append(cause.getMessage());
            }
            cause = cause.getCause();
        }

        return message.toString();
    }

    /**
     * Converts all relative paths in the command line to absolute paths.
     * This is necessary for in-thread execution because setting "user.dir" doesn't affect
     * File resolution in an already-running JVM.
     */
    private CommandLine convertToAbsolutePaths(final CommandLine cmd) {
        final CommandLine absoluteCmd = new CommandLine(cmd.getMainClassName(), cmd.getJvmArgs());

        for (final String arg : cmd.toList()) {
            // Check if this looks like a file path (not a flag starting with -)
            if (!arg.startsWith("-") && (arg.contains("/") || arg.contains(File.separator))) {
                final File file = new File(arg);
                if (!file.isAbsolute()) {
                    // Convert relative path to absolute based on working directory
                    final File absoluteFile = new File(context.getWorkingDirectory(), arg);
                    absoluteCmd.addRaw(absoluteFile.getAbsolutePath());
                    continue;
                }
            }
            // Keep the argument as-is (flags, absolute paths, etc.)
            absoluteCmd.addRaw(arg);
        }

        return absoluteCmd;
    }

    /**
     * Invokes the compiler in a forked process using ProcessBuilder.
     * This is the fallback path when forking is explicitly requested or required.
     * This method handles the try-catch wrapper and delegates to executeForkedProcess.
     */
    protected ExecResult invokeForked(final CommandLine cmd, final File javaToolsJar) {
        try {
            return executeForkedProcess(cmd, javaToolsJar);
        } catch (final IOException | InterruptedException e) {
            logger.error("[plugin] Forked invocation failed with exception", e);
            return SimpleExecResult.failure(-1, e);
        }
    }

    /**
     * Builds the command line for forked process execution.
     * This is a helper method used by both normal and detached execution modes.
     *
     * @param cmd The command line to execute
     * @param javaToolsJar The javatools JAR file
     * @return The command list ready for ProcessBuilder
     */
    protected List<String> buildForkedCommand(final CommandLine cmd, final File javaToolsJar) {
        final String javaExecutable = context.getToolchainExecutable().getOrNull();
        if (javaExecutable == null) {
            throw new GradleException("[plugin] No Java toolchain executable found - cannot fork process");
        }
        logger.info("[plugin] Using Java toolchain executable: {}", javaExecutable);
        final List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        command.addAll(cmd.getJvmArgs());
        command.add("-cp");
        command.add(javaToolsJar.getAbsolutePath());
        command.add(cmd.getMainClassName());
        command.addAll(cmd.toList());
        return command;
    }

    /**
     * Executes the forked process. Subclasses can override this to customize the execution behavior.
     * This method is called within a try-catch block by invokeForked().
     *
     * @param cmd The command line to execute
     * @param javaToolsJar The javatools JAR file
     * @return The execution result
     * @throws IOException if the process cannot be started
     * @throws InterruptedException if the process is interrupted while waiting
     */
    protected ExecResult executeForkedProcess(final CommandLine cmd, final File javaToolsJar)
            throws IOException, InterruptedException {
        logger.info("[plugin] Invoking {} in forked process", cmd.getIdentifier());

        final var command = buildForkedCommand(cmd, javaToolsJar);
        final var processBuilder = new ProcessBuilder(command).directory(context.getWorkingDirectory());

        // Configure I/O redirection based on DSL settings
        configureIoRedirection(processBuilder, cmd);

        logger.info("[plugin] Starting forked process: {}", String.join(" ", command));
        final Process process = processBuilder.start();
        final int exitCode = process.waitFor();

        if (exitCode != 0) {
            final Exception failure = new GradleException("Forked process exited with code: " + exitCode);
            logger.error("[plugin] Forked process failed with exit code: {}", exitCode);
            return SimpleExecResult.failure(exitCode, failure);
        } else {
            logger.info("[plugin] Forked process completed successfully");
            return SimpleExecResult.success(exitCode);
        }
    }

    /**
     * Configures I/O redirection for the process builder based on DSL settings.
     * Subclasses can override getDefaultStdoutPath() and getDefaultStderrPath() to customize defaults.
     *
     * @param processBuilder The process builder to configure
     * @param cmd The command line being executed
     */
    protected void configureIoRedirection(final ProcessBuilder processBuilder, final CommandLine cmd) {
        final String stdoutPath = resolveOutputPath(task.hasStdoutRedirect(), task.getStdoutPath(), getDefaultStdoutPath(cmd));
        final String stderrPath = resolveOutputPath(task.hasStderrRedirect(), task.getStderrPath(), getDefaultStderrPath(cmd));

        configureStream(processBuilder::redirectOutput, stdoutPath, "stdout");
        configureStream(processBuilder::redirectError, stderrPath, "stderr");
        processBuilder.redirectInput(ProcessBuilder.Redirect.INHERIT);

        if (stdoutPath == null && stderrPath == null) {
            logger.info("[plugin] Using inherited I/O (console output)");
        }
    }

    private String resolveOutputPath(final boolean hasCustomPath, final Property<@NotNull String> customPath, final String defaultPath) {
        return hasCustomPath ? expandTimestampPlaceholder(customPath.get()) : defaultPath;
    }

    private void configureStream(
            final Consumer<ProcessBuilder.Redirect> redirectSetter,
            final String path,
            final String streamName) {
        if (path != null) {
            final File file = new File(context.getWorkingDirectory(), path);
            redirectSetter.accept(ProcessBuilder.Redirect.appendTo(file));
            logger.lifecycle("[plugin] Redirecting {} to: {}", streamName, file.getAbsolutePath());
        } else {
            redirectSetter.accept(ProcessBuilder.Redirect.INHERIT);
        }
    }

    /**
     * Returns the default stdout path when no DSL configuration is specified.
     * Default implementation returns null (inherit from parent process).
     * Subclasses (like DetachedJavaClasspathLauncher) can override to provide file-based defaults.
     *
     * @param cmd The command being executed
     * @return The default stdout path, or null to inherit from parent process
     */
    protected String getDefaultStdoutPath(final CommandLine cmd) {
        return null; // Normal fork mode: inherit stdout by default
    }

    /**
     * Returns the default stderr path when no DSL configuration is specified.
     * Default implementation returns null (inherit from parent process).
     * Subclasses (like DetachedJavaClasspathLauncher) can override to provide file-based defaults.
     *
     * @param cmd The command being executed
     * @return The default stderr path, or null to inherit from parent process
     */
    protected String getDefaultStderrPath(final CommandLine cmd) {
        return null; // Normal fork mode: inherit stderr by default
    }
}
