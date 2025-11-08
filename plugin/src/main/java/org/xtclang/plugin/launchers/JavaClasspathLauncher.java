package org.xtclang.plugin.launchers;

import static org.xtclang.plugin.XtcPluginConstants.XDK_JAVATOOLS_NAME_JAR;
import static org.xtclang.plugin.launchers.XtcExecResult.XtcExecResultBuilder;

import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.process.ExecResult;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcJavaToolsRuntime;
import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.tasks.XtcLauncherTask;

import org.xvm.tool.Compiler;
import org.xvm.tool.Disassembler;
import org.xvm.tool.Launcher.LauncherException;
import org.xvm.tool.Runner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    private final Provider<@NotNull String> projectVersion;
    private final Provider<@NotNull FileTree> xdkFileTree;
    private final Provider<@NotNull FileCollection> javaToolsConfig;
    private final Provider<@NotNull String> toolchainExecutable;
    private final File workingDirectory;
    private final boolean fork;

    /**
     * Creates a new JavaClasspathLauncher.
     *
     * @param task The task being executed
     * @param logger Logger for diagnostic output
     * @param projectVersion The project version for artifact resolution
     * @param xdkFileTree The XDK file tree for resolving javatools
     * @param javaToolsConfig The javatools configuration
     * @param toolchainExecutable The Java toolchain executable path
     * @param workingDirectory The working directory for process execution
     * @param fork Whether to fork to a separate process
     */
    public JavaClasspathLauncher(
            final T task,
            final Logger logger,
            final Provider<@NotNull String> projectVersion,
            final Provider<@NotNull FileTree> xdkFileTree,
            final Provider<@NotNull FileCollection> javaToolsConfig,
            final Provider<@NotNull String> toolchainExecutable,
            final File workingDirectory,
            final boolean fork) {
        super(task, logger);
        this.projectVersion = projectVersion;
        this.xdkFileTree = xdkFileTree;
        this.javaToolsConfig = javaToolsConfig;
        this.toolchainExecutable = toolchainExecutable;
        this.workingDirectory = workingDirectory;
        this.fork = fork;
    }

    protected Provider<@NotNull String> getToolchainExecutable() {
        return toolchainExecutable;
    }

    protected File getWorkingDirectory() {
        return workingDirectory;
    }

    protected Provider<@NotNull String> getProjectVersion() {
        return projectVersion;
    }

    protected Provider<@NotNull FileTree> getXdkFileTree() {
        return xdkFileTree;
    }

    protected Provider<@NotNull FileCollection> getJavaToolsConfig() {
        return javaToolsConfig;
    }

    @Override
    public ExecResult apply(final CommandLine cmd) {
        logger.info("[plugin] Launching task (project version: {}), with JavaClasspathLauncher: {}", projectVersion, this);

        final var javaToolsJar = XtcJavaToolsRuntime.resolveJavaTools(projectVersion, javaToolsConfig, xdkFileTree, logger);
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
                [plugin] ===== EXACT EXECUTION DETAILS =====
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
                workingDirectory.getAbsolutePath(),
                cmd.toList().size(),
                argsBuilder.toString());
        logger.info(infoLog);
        final var builder = resultBuilder(cmd);

        if (fork) {
            return invokeForked(cmd, javaToolsJar, builder);
        } else {
            return invokeDirectly(cmd, javaToolsJar, builder);
        }
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
    private ExecResult invokeDirectly(final CommandLine cmd, final File javaToolsJar, final XtcExecResult.XtcExecResultBuilder builder) {
        logger.lifecycle("[plugin] Invoking {} directly in current thread (no fork)", cmd.getIdentifier());

        try {
            // Convert relative paths to absolute paths for in-thread execution
            // (Setting user.dir doesn't affect File resolution in an already-running JVM)
            final CommandLine absoluteCmd = convertToAbsolutePaths(cmd);

            // Use the utility to execute with javatools on classpath
            // Inside withJavaTools(), the context classloader is set so we can call javatools DIRECTLY!
            return XtcJavaToolsRuntime.withJavaTools(javaToolsJar, logger, () -> {
                // Convert command line to string array
                final String[] args = absoluteCmd.toList().toArray(new String[0]);
                try {
                    // Direct call - NO REFLECTION! The compileOnly dependency gives us the types,
                    // and withJavaTools() sets up the classloader so exceptions work correctly
                    final String mainClass = cmd.getMainClassName();
                    // Call the appropriate launcher - all extend Launcher and have the same launch() signature
                    switch (mainClass) {
                        case "org.xvm.tool.Compiler":
                            Compiler.launch(args);
                            break;
                        case "org.xvm.tool.Runner":
                            Runner.launch(args);
                            break;
                        case "org.xvm.tool.Disassembler":
                            Disassembler.launch(args);
                            break;
                        default:
                            throw new GradleException("Unsupported tool: " + mainClass);
                    }

                    // If we get here, the tool succeeded
                    builder.exitValue(0);
                    logger.info("[plugin] {} completed successfully", mainClass);
                    return createExecResult(builder);

                } catch (final LauncherException e) {
                    // Expected exit mechanism from launcher - this works because we're in the
                    // same classloader context thanks to withJavaTools()
                    final int exitCode = e.error ? 1 : 0;
                    builder.exitValue(exitCode);

                    if (exitCode != 0) {
                        // IMPORTANT: We would ideally wrap LauncherException (and its cause) directly in
                        // GradleException to preserve the full exception chain. From a Java classloader
                        // perspective, this is perfectly safe - ensureJavaToolsInClasspath() has loaded
                        // javatools.jar into the plugin's classloader, so LauncherException is accessible
                        // and there are no classloader conflicts.
                        //
                        // HOWEVER, Gradle's configuration cache cannot serialize arbitrary exception types,
                        // even when they're from the same classloader. The configuration cache tries to
                        // serialize the exception graph, and LauncherException (or its causes) may contain
                        // references to non-serializable javatools types. This causes configuration cache
                        // serialization to fail.
                        //
                        // Therefore, we must extract the exception information as pure strings before
                        // creating the GradleException. This workaround exists solely due to configuration
                        // cache serialization constraints, not classloader issues.
                        final String errorMessage = buildErrorMessage(e);
                        final GradleException gradleException = new GradleException(
                                "XTC tool execution failed: " + errorMessage);
                        builder.failure(gradleException);
                        logger.error("[plugin] {} failed with exit code: {}", cmd.getMainClassName(), exitCode);
                    } else {
                        logger.info("[plugin] {} completed with exit code: {}", cmd.getMainClassName(), exitCode);
                    }

                    return createExecResult(builder);
                }
            });

        } catch (final Exception e) {
            logger.error("[plugin] Direct invocation failed with exception", e);
            builder.exitValue(-1);
            builder.failure(e);
            return createExecResult(builder);
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
                    final File absoluteFile = new File(workingDirectory, arg);
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
    protected ExecResult invokeForked(final CommandLine cmd, final File javaToolsJar, final XtcExecResult.XtcExecResultBuilder builder) {
        try {
            return executeForkedProcess(cmd, javaToolsJar, builder);
        } catch (final IOException | InterruptedException e) {
            logger.error("[plugin] Forked invocation failed with exception", e);
            builder.exitValue(-1);
            builder.failure(e);
            return createExecResult(builder);
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
        final String javaExecutable = toolchainExecutable.getOrNull();
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
     * @param builder The result builder
     * @return The execution result
     * @throws IOException if the process cannot be started
     * @throws InterruptedException if the process is interrupted while waiting
     */
    protected ExecResult executeForkedProcess(final CommandLine cmd, final File javaToolsJar, final XtcExecResultBuilder builder)
            throws IOException, InterruptedException {
        logger.lifecycle("[plugin] Invoking {} in forked process", cmd.getIdentifier());

        final List<String> command = buildForkedCommand(cmd, javaToolsJar);

        final ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(workingDirectory);

        // Normal fork mode: inherit IO and wait for completion
        processBuilder.inheritIO();
        logger.info("[plugin] Starting forked process: {}", String.join(" ", command));
        final Process process = processBuilder.start();
        final int exitCode = process.waitFor();
        builder.exitValue(exitCode);

        if (exitCode != 0) {
            final Exception failure = new GradleException("Forked process exited with code: " + exitCode);
            builder.failure(failure);
            logger.error("[plugin] Forked process failed with exit code: {}", exitCode);
        } else {
            logger.lifecycle("[plugin] Forked process completed successfully");
        }
        return createExecResult(builder);
    }

}