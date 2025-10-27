package org.xtclang.plugin.launchers;

import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.process.ExecResult;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.services.XtcCompilerService;
import org.xtclang.plugin.tasks.XtcLauncherTask;

import java.util.Objects;

/**
 * Launcher that uses the XTC Compiler Daemon (Build Service) to compile XTC source files
 * without spawning a new Java process for each compilation.
 *
 * <p>This launcher provides significant performance improvements over {@link JavaExecLauncher}
 * by reusing a persistent compiler instance across all compilation tasks in the build.
 *
 * <p><b>Benefits:</b>
 * <ul>
 *   <li>No JVM startup overhead for each compilation</li>
 *   <li>ClassLoader and class metadata reused across compilations</li>
 *   <li>JIT compilation benefits from warmed-up code paths</li>
 *   <li>Thread-safe and configuration-cache compatible</li>
 * </ul>
 *
 * <p><b>vs BuildThreadLauncher:</b> Unlike BuildThreadLauncher which directly invokes the
 * compiler in the current thread, this launcher uses a Build Service for proper lifecycle
 * management and better isolation.
 *
 * <p><b>vs JavaExecLauncher:</b> Unlike JavaExecLauncher which forks a new JVM process for
 * each compilation, this launcher reuses a persistent compiler instance, eliminating process
 * startup overhead.
 *
 * @param <E> The extension type
 * @param <T> The task type
 */
public class CompilerDaemonLauncher<E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>>
        extends XtcLauncher<E, T> {

    private final Provider<XtcCompilerService> compilerServiceProvider;

    public CompilerDaemonLauncher(
            @NotNull final T task,
            @NotNull final Logger logger,
            @NotNull final Provider<XtcCompilerService> compilerServiceProvider) {
        super(task, logger);
        this.compilerServiceProvider = Objects.requireNonNull(compilerServiceProvider,
                "Compiler service provider cannot be null");
    }

    @Override
    protected boolean validateCommandLine(@NotNull final CommandLine cmd) {
        Objects.requireNonNull(cmd, "Command line cannot be null");

        final var mainClassName = cmd.getMainClassName();
        logger.info("[compiler-daemon] Task will use compiler daemon for: {}", mainClassName);

        // JVM args are not applicable for daemon - the daemon JVM is already running
        final var jvmArgs = cmd.getJvmArgs();
        if (!jvmArgs.isEmpty()) {
            logger.warn("[compiler-daemon] WARNING: JVM args ({}) are ignored when using compiler daemon. " +
                    "The daemon runs with the Gradle daemon's JVM settings.", jvmArgs);
        }

        return true;
    }

    @Override
    public ExecResult apply(@NotNull final CommandLine cmd) {
        logger.info("[compiler-daemon] Compiling using XTC compiler daemon: {}", task.getName());
        validateCommandLine(cmd);
        final var builder = resultBuilder(cmd);
        try {
            if (task.hasVerboseLogging()) {
                logger.lifecycle("[compiler-daemon] Compiler command: {}", cmd.toString());
            }
            // Get the compiler service instance
            final XtcCompilerService compilerService = compilerServiceProvider.get();
            // Compile using the daemon
            final XtcExecResult result = compilerService.compile(
                    cmd,
                    task.getJavaToolsClasspath(),
                    logger
            );
            // Transfer result to builder
            builder.exitValue(result.getExitValue());
            if (result.getFailure() != null) {
                builder.failure(result.getFailure());
            }

        } catch (final Exception e) {
            logger.error("[compiler-daemon] Compilation failed with unexpected exception", e);
            builder.exitValue(-1);
            builder.failure(e);
        }
        return createExecResult(builder);
    }

    @Override
    public String toString() {
        return "CompilerDaemonLauncher{task=" + task.getName() + "}";
    }
}
