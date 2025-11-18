package org.xtclang.plugin.launchers;

import static org.xtclang.plugin.XtcJavaToolsRuntime.resolveJavaTools;
import static org.xtclang.plugin.XtcPluginConstants.XDK_JAVATOOLS_NAME_JAR;
import static org.xtclang.plugin.XtcPluginUtils.expandTimestampPlaceholder;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Property;
import org.gradle.process.ExecResult;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.tasks.XtcLauncherTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
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
    private final boolean fork;

    /**
     * Creates a new JavaClasspathLauncher.
     *
     * @param task The task being executed
     * @param logger Logger for diagnostic output
     * @param context Launcher execution context (project version, XDK, javatools, etc.)
     * @param fork Whether to fork to a separate process
     */
    public JavaClasspathLauncher(
            final T task,
            final Logger logger,
            final LauncherContext context,
            final boolean fork) {
        super(task, logger);
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
     * Invokes the compiler directly using the JavaToolsBridge pattern.
     *
     * <p>This method uses a custom classloader to load the JavaToolsBridge class with
     * both the plugin JAR (containing the bridge class file) and javatools.jar (containing
     * javatools types) on its classpath. This allows the bridge to directly reference
     * javatools types (like LauncherException) without requiring them on the plugin's
     * main classloader.
     *
     * <p>This works in both scenarios:
     * <ul>
     *   <li>XDK development: javatools.jar from local build</li>
     *   <li>Third-party (xtc-app-template): javatools.jar from extracted distribution</li>
     * </ul>
     */
    private ExecResult invokeDirectly(final CommandLine cmd, final File javaToolsJar) {
        logger.info("[plugin] Invoking {} directly in current thread (no fork)", cmd.getIdentifier());

        try {
            // Convert relative paths to absolute paths for in-thread execution mode, we cannot just reset
            // user.dir if we are not forking launchers.
            // TODO: We might remove direct invocation mode, but we want to fully investigate it for overhead first,
            //  and it is also useful to make sure the XDK we need to use for language support is reentrant.
            final CommandLine absoluteCmd = convertToAbsolutePaths(cmd);
            final String[] args = absoluteCmd.toList().toArray(new String[0]);

            // Get the plugin JAR location (contains the bridge class file)
            try (URLClassLoader bridgeClassLoader = getBridgeClassLoader(javaToolsJar)) {
                // Load the bridge class via our custom classloader
                // Get the execute method
                // Determine launcher type from the tool launcher class name
                // Invoke via the bridge - this is where LauncherException can be caught
                final Class<?> bridgeClass = bridgeClassLoader.loadClass("org.xtclang.plugin.javatools.JavaToolsBridge");
                final Object bridge = bridgeClass.getDeclaredConstructor().newInstance();
                final Method executeMethod = bridgeClass.getMethod("execute", String.class, String[].class);
                final String launcherType = cmd.getIdentifier().toLowerCase(); // "compiler" or "runner"
                final Object result = executeMethod.invoke(bridge, launcherType, args);

                // Extract results from BridgeResult
                final Class<?> resultClass = result.getClass();
                final boolean success = (Boolean) resultClass.getField("success").get(result);
                final int exitCode = (Integer) resultClass.getField("exitCode").get(result);
                final String errorMessage = (String) resultClass.getField("errorMessage").get(result);

                if (success) {
                    logger.info("[plugin] Tool execution completed successfully");
                    return SimpleExecResult.success(exitCode);
                }
                logger.error("[plugin] Tool execution failed with exit code: {}", exitCode);
                final GradleException gradleException = new GradleException("XTC tool execution failed: " + errorMessage);
                return SimpleExecResult.failure(exitCode, gradleException);
            }
        } catch (final Exception e) {
            logger.error("[plugin] Direct invocation failed with exception", e);
            return SimpleExecResult.failure(-1, e);
        }
    }

    /**
     * Create a custom classloader with both plugin JAR and javatools JAR
     * This allows the bridge class to load with javatools types available
     * null parent = isolated
     *
     * @param javaToolsJar resolved path to "javatools.jar"
     * @return An URLClassLoader that contains the plugin classes and the javatools classes for the jar.
     *
     * @throws URISyntaxException on resolution or syntax error
     * @throws MalformedURLException on resolution or syntax error
     */
    private @NotNull URLClassLoader getBridgeClassLoader(File javaToolsJar) throws URISyntaxException, MalformedURLException {
        final File pluginJar = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
        return new URLClassLoader(new URL[] { pluginJar.toURI().toURL(), javaToolsJar.toURI().toURL() }, null);
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

        // Determine which streams need to be copied to console
        final boolean copyStdout = !task.hasStdoutRedirect() && getDefaultStdoutPath(cmd) == null;
        final boolean copyStderr = !task.hasStderrRedirect() && getDefaultStderrPath(cmd) == null;

        configureIoRedirection(processBuilder, cmd);

        logger.info("[plugin] Starting forked process: {}", String.join(" ", command));
        final Process process = processBuilder.start();

        // Copy streams that aren't redirected to files
        final var streamThreads = (copyStdout || copyStderr) ? copyProcessStreams(process, copyStdout, copyStderr) : null;

        final int exitCode = process.waitFor();

        // Wait for stream copying threads to finish
        if (streamThreads != null) {
            for (final var thread : streamThreads) {
                thread.join();
            }
        }

        if (exitCode != 0) {
            final Exception failure = new GradleException("Forked process exited with code: " + exitCode);
            logger.error("[plugin] Forked process failed with exit code: {}", exitCode);
            return SimpleExecResult.failure(exitCode, failure);
        }
        logger.info("[plugin] Forked process completed successfully");
        return SimpleExecResult.success(exitCode);
    }

    /**
     * Copies process stdout/stderr to System.out/err in background threads.
     * This is necessary when Gradle has redirected file descriptors and inheritIO() doesn't work.
     * Uses byte-buffer based copying (like Gradle's implementation) for better performance and
     * to capture all output including partial lines.
     *
     * @param process the process whose streams to copy
     * @param copyStdout whether to copy stdout to System.out
     * @param copyStderr whether to copy stderr to System.err
     * @return list of threads copying the streams
     */
    private List<Thread> copyProcessStreams(final Process process, final boolean copyStdout, final boolean copyStderr) {
        record StreamCopy(InputStream input, OutputStream output, String name) {}

        final var streams = new ArrayList<StreamCopy>(2);
        if (copyStdout) {
            streams.add(new StreamCopy(process.getInputStream(), System.out, "xtc-stdout-copy"));
        }
        if (copyStderr) {
            streams.add(new StreamCopy(process.getErrorStream(), System.err, "xtc-stderr-copy"));
        }

        final var threads = new ArrayList<Thread>(streams.size());
        for (final var stream : streams) {
            final var thread = new Thread(() -> {
                try {
                    final byte[] buffer = new byte[8192]; // 8KB buffer like Gradle
                    int bytesRead;
                    while ((bytesRead = stream.input.read(buffer)) != -1) {
                        stream.output.write(buffer, 0, bytesRead);
                        stream.output.flush();
                    }
                } catch (final IOException e) {
                    logger.warn("[plugin] Error copying {}: {}", stream.name, e.getMessage());
                } finally {
                    try {
                        stream.input.close();
                    } catch (final IOException e) {
                        // Ignore close errors
                    }
                }
            }, stream.name);
            thread.start();
            threads.add(thread);
        }
        return threads;
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

        // Configure each stream independently - redirect to file if specified, otherwise leave as PIPE
        if (stdoutPath != null) {
            configureStream(processBuilder::redirectOutput, stdoutPath, "stdout");
        }
        if (stderrPath != null) {
            configureStream(processBuilder::redirectError, stderrPath, "stderr");
        }
        processBuilder.redirectInput(ProcessBuilder.Redirect.INHERIT);

        if (stdoutPath == null && stderrPath == null) {
            logger.info("[plugin] Using inherited I/O (console output)");
        } else if (stdoutPath != null && stderrPath != null) {
            logger.info("[plugin] Redirecting both stdout and stderr to files");
        } else {
            logger.info("[plugin] Redirecting {} to file, {} to console",
                    stdoutPath != null ? "stdout" : "stderr",
                    stdoutPath != null ? "stderr" : "stdout");
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
            logger.info("[plugin] Redirecting {} to: {}", streamName, file.getAbsolutePath());
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
