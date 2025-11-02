package org.xtclang.plugin.launchers;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.process.ExecResult;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.tasks.XtcLauncherTask;

import java.io.File;
import java.util.Objects;

/**
 * Launcher that delegates to JavaClasspathLauncher for in-process compilation.
 * Previously maintained a persistent compiler instance, but now simply uses
 * JavaClasspathLauncher with fork=false for consistency.
 *
 * @param <E> The extension type
 * @param <T> The task type
 */
public class CompilerDaemonLauncher<E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>>
        extends XtcLauncher<E, T> {

    private final Provider<@NotNull String> projectVersion;
    private final Provider<@NotNull FileTree> xdkFileTree;
    private final Provider<@NotNull FileCollection> javaToolsConfig;
    private final Provider<@NotNull String> toolchainExecutable;
    private final File projectDirectory;

    public CompilerDaemonLauncher(
            @NotNull final T task,
            @NotNull final Logger logger,
            @NotNull final Provider<@NotNull String> projectVersion,
            @NotNull final Provider<@NotNull FileTree> xdkFileTree,
            @NotNull final Provider<@NotNull FileCollection> javaToolsConfig,
            @NotNull final Provider<@NotNull String> toolchainExecutable,
            @NotNull final File projectDirectory) {
        super(task, logger);
        this.projectVersion = projectVersion;
        this.xdkFileTree = xdkFileTree;
        this.javaToolsConfig = javaToolsConfig;
        this.toolchainExecutable = toolchainExecutable;
        this.projectDirectory = projectDirectory;
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
        logger.lifecycle("[compiler-daemon] Using in-process compilation (delegates to JavaClasspathLauncher)");
        validateCommandLine(cmd);

        // Delegate to JavaClasspathLauncher with fork=false for in-process execution
        final JavaClasspathLauncher<E, T> launcher = new JavaClasspathLauncher<>(
            task,
            logger,
            projectVersion,
            xdkFileTree,
            javaToolsConfig,
            toolchainExecutable,
            projectDirectory,
            false // fork=false for in-process execution
        );

        return launcher.apply(cmd);
    }

    @Override
    public String toString() {
        return "CompilerDaemonLauncher{task=" + task.getName() + "}";
    }
}
