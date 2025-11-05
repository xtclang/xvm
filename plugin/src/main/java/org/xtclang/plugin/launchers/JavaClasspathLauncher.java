package org.xtclang.plugin.launchers;

import static org.xtclang.plugin.XtcPluginConstants.XDK_JAVATOOLS_NAME_JAR;

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
import org.xvm.tool.Launcher;
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
    private final File projectDirectory;
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
     * @param projectDirectory The project directory for working directory
     * @param fork Whether to fork to a separate process
     */
    public JavaClasspathLauncher(
            final T task,
            final Logger logger,
            final Provider<@NotNull String> projectVersion,
            final Provider<@NotNull FileTree> xdkFileTree,
            final Provider<@NotNull FileCollection> javaToolsConfig,
            final Provider<@NotNull String> toolchainExecutable,
            final File projectDirectory,
            final boolean fork) {
        super(task, logger);
        this.projectVersion = projectVersion;
        this.xdkFileTree = xdkFileTree;
        this.javaToolsConfig = javaToolsConfig;
        this.toolchainExecutable = toolchainExecutable;
        this.projectDirectory = projectDirectory;
        this.fork = fork;
    }

    @Override
    public ExecResult apply(final CommandLine cmd) {
        logger.info("[plugin] Launching task with JavaClasspathLauncher: {}", this);

        final var javaToolsJar = XtcJavaToolsRuntime.resolveJavaTools(
                projectVersion, javaToolsConfig, xdkFileTree, logger);

        logger.info("[plugin] {} (launcher: {}); Using '{}' in classpath from: {}",
                cmd.getIdentifier(), getClass().getSimpleName(), XDK_JAVATOOLS_NAME_JAR, javaToolsJar);

        if (task.hasVerboseLogging()) {
            final var launchLine = cmd.toString(javaToolsJar);
            logger.lifecycle("[plugin] JavaClasspath command (launcher {}, fork={}): {}",
                    getClass().getSimpleName(), fork, launchLine);
        }

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

                    // Assert we're calling a known javatools Launcher subclass
                    assert mainClass.equals("org.xvm.tool.Compiler") ||
                           mainClass.equals("org.xvm.tool.Runner") ||
                           mainClass.equals("org.xvm.tool.Disassembler") :
                           "Unknown main class: " + mainClass;

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
                    logger.lifecycle("[plugin] {} completed successfully", mainClass);
                    return createExecResult(builder);

                } catch (final LauncherException e) {
                    // Expected exit mechanism from launcher - this works because we're in the
                    // same classloader context thanks to withJavaTools()
                    final int exitCode = e.error ? 1 : 0;
                    builder.exitValue(exitCode);

                    if (exitCode != 0) {
                        // IMPORTANT: Don't store LauncherException directly - it's from javatools classloader
                        // and Gradle can't serialize it. Extract message only, no cause reference!
                        final String errorMessage = e.getMessage() != null ? e.getMessage() : "Execution failed";
                        final GradleException gradleException = new GradleException(
                                "XTC tool execution failed: " + errorMessage);
                        builder.failure(gradleException);
                        logger.error("[plugin] {} failed with exit code: {}", cmd.getMainClassName(), exitCode);
                    } else {
                        logger.lifecycle("[plugin] {} completed with exit code: {}", cmd.getMainClassName(), exitCode);
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
     * Converts all relative paths in the command line to absolute paths.
     * This is necessary for in-thread execution because setting user.dir doesn't affect
     * File resolution in an already-running JVM.
     */
    private CommandLine convertToAbsolutePaths(final CommandLine cmd) {
        final CommandLine absoluteCmd = new CommandLine(cmd.getMainClassName(), cmd.getJvmArgs());

        for (final String arg : cmd.toList()) {
            // Check if this looks like a file path (not a flag starting with -)
            if (!arg.startsWith("-") && (arg.contains("/") || arg.contains(File.separator))) {
                final File file = new File(arg);
                if (!file.isAbsolute()) {
                    // Convert relative path to absolute based on project directory
                    final File absoluteFile = new File(projectDirectory, arg);
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
     */
    private ExecResult invokeForked(final CommandLine cmd, final File javaToolsJar, final XtcExecResult.XtcExecResultBuilder builder) {
        logger.lifecycle("[plugin] Invoking {} in forked process", cmd.getIdentifier());

        try {
            // Use configured Java toolchain executable
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
            final ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.inheritIO();
            processBuilder.directory(projectDirectory);
            logger.info("[plugin] Starting forked process: {}", String.join(" ", command));
            // Start process and wait for completion
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

        } catch (final IOException | InterruptedException e) {
            logger.error("[plugin] Forked invocation failed with exception", e);
            builder.exitValue(-1);
            builder.failure(e);
            return createExecResult(builder);
        }
    }

}