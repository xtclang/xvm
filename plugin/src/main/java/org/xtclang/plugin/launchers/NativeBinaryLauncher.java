package org.xtclang.plugin.launchers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.internal.DefaultXtcLauncherTaskExtension;
import org.xtclang.plugin.tasks.XtcLauncherTask;

public class NativeBinaryLauncher<E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>> extends XtcLauncher<E, T> {

    protected final String commandName;
    private final ExecOperations execOperations;
    private final boolean useProcessBuilder;

    public NativeBinaryLauncher(final T task, final Logger logger, final ExecOperations execOperations) {
        this(task, logger, execOperations, false);
    }

    /**
     * Constructor with option to use ProcessBuilder directly for better concurrency control.
     *
     * @param task The launcher task
     * @param logger The logger
     * @param execOperations Gradle's exec operations
     * @param useProcessBuilder If true, uses ProcessBuilder directly instead of ExecOperations for better parallel execution support
     */
    public NativeBinaryLauncher(final T task, final Logger logger, final ExecOperations execOperations, final boolean useProcessBuilder) {
        super(task, logger);
        this.commandName = task.getNativeLauncherCommandName();
        this.execOperations = execOperations;
        this.useProcessBuilder = useProcessBuilder;
    }

    @Override
    protected boolean validateCommandLine(final CommandLine cmd) {
        final var mainClassName = cmd.getMainClassName();
        final var jvmArgs = cmd.getJvmArgs();
        if (DefaultXtcLauncherTaskExtension.areJvmArgsModified(jvmArgs)) {
            logger.warn("[plugin] WARNING: Launcher for mainClassName '{}' has non-default JVM args ({}). These are ignored, as we are running a native launcher.",
                mainClassName, jvmArgs);
        }
        return super.validateCommandLine(cmd);
    }

    @Override
    public ExecResult apply(final CommandLine cmd) {
        logger.info("[plugin] Launching task: {}}", this);
        validateCommandLine(cmd);
        if (task.hasVerboseLogging()) {
            logger.lifecycle("[plugin] NativeExec command: {}", cmd.toString());
        }

        if (useProcessBuilder) {
            return executeWithProcessBuilder(cmd);
        } else {
            return executeWithExecOperations(cmd);
        }
    }

    /**
     * Execute using Gradle's ExecOperations (traditional method).
     */
    private ExecResult executeWithExecOperations(final CommandLine cmd) {
        final var builder = resultBuilder(cmd);
        final var execResult = execOperations.exec(spec -> {
            redirectIo(spec);
            // Gradle ExecOperations automatically resolves executables from system PATH
            spec.setExecutable(commandName);
            spec.setArgs(cmd.toList());
            spec.setIgnoreExitValue(true);
        });
        return createExecResult(builder.execResult(execResult));
    }

    /**
     * Execute using ProcessBuilder directly for better concurrency control.
     * This allows multiple ProcessBuilders to run in parallel more efficiently.
     */
    private ExecResult executeWithProcessBuilder(final CommandLine cmd) {
        final var builder = resultBuilder(cmd);

        try {
            final var processCmd = new ArrayList<String>();
            processCmd.add(commandName);
            processCmd.addAll(cmd.toList());

            final ProcessBuilder processBuilder = new ProcessBuilder(processCmd);

            // Redirect I/O streams
            if (task.hasStdinRedirect()) {
                // ProcessBuilder doesn't support InputStream directly, so we'll handle it differently
                processBuilder.redirectInput(ProcessBuilder.Redirect.PIPE);
            } else {
                processBuilder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            }

            if (task.hasStdoutRedirect()) {
                processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
            } else {
                processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }

            if (task.hasStderrRedirect()) {
                processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);
            } else {
                processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
            }

            logger.debug("[plugin] Starting process with ProcessBuilder: {}", processCmd);
            final Process process = processBuilder.start();

            // Handle stdin if redirected
            if (task.hasStdinRedirect()) {
                CompletableFuture.runAsync(() -> {
                    try (final OutputStream os = process.getOutputStream();
                         final InputStream is = task.getStdin().get()) {
                        is.transferTo(os);
                    } catch (final IOException e) {
                        logger.warn("[plugin] Error writing to process stdin", e);
                    }
                });
            }

            // Handle stdout if redirected
            if (task.hasStdoutRedirect()) {
                CompletableFuture.runAsync(() -> {
                    try (final InputStream is = process.getInputStream();
                         final OutputStream os = task.getStdout().get()) {
                        is.transferTo(os);
                    } catch (final IOException e) {
                        logger.warn("[plugin] Error reading from process stdout", e);
                    }
                });
            }

            // Handle stderr if redirected
            if (task.hasStderrRedirect()) {
                CompletableFuture.runAsync(() -> {
                    try (final InputStream is = process.getErrorStream();
                         final OutputStream os = task.getStderr().get()) {
                        is.transferTo(os);
                    } catch (final IOException e) {
                        logger.warn("[plugin] Error reading from process stderr", e);
                    }
                });
            }

            final int exitCode = process.waitFor();
            logger.debug("[plugin] Process completed with exit code: {}", exitCode);

            // Create a simple ExecResult wrapper
            final ExecResult execResult = new ExecResult() {
                @Override
                public int getExitValue() {
                    return exitCode;
                }

                @Override
                public ExecResult assertNormalExitValue() {
                    if (exitCode != 0) {
                        throw new GradleException(
                            String.format("Process '%s' finished with non-zero exit value %d", commandName, exitCode));
                    }
                    return this;
                }

                @Override
                public ExecResult rethrowFailure() {
                    return assertNormalExitValue();
                }
            };

            return createExecResult(builder.execResult(execResult));

        } catch (final IOException e) {
            throw new GradleException("[plugin] Failed to start process: " + commandName, e);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GradleException("[plugin] Process interrupted: " + commandName, e);
        }
    }

    /**
     * Execute multiple commands in parallel using ProcessBuilder.
     * This is useful when Gradle's parallel flag is enabled.
     *
     * @param commands List of commands to execute in parallel
     * @return List of execution results
     */
    public List<ExecResult> applyParallel(final List<CommandLine> commands) {
        if (!useProcessBuilder) {
            logger.warn("[plugin] Parallel execution requested but useProcessBuilder is false. Falling back to sequential execution.");
            return commands.stream().map(this::apply).toList();
        }

        logger.lifecycle("[plugin] Executing {} commands in parallel using ProcessBuilder", commands.size());

        final List<CompletableFuture<ExecResult>> futures = commands.stream()
            .map(cmd -> CompletableFuture.supplyAsync(() -> apply(cmd)))
            .toList();

        // Wait for all to complete
        final CompletableFuture<Void> allOf = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture<?>[0])
        );

        try {
            allOf.get(); // Wait for all processes to complete
            return futures.stream().map(CompletableFuture::join).toList();
        } catch (final Exception e) {
            throw new GradleException("[plugin] Error during parallel execution", e);
        }
    }
}
