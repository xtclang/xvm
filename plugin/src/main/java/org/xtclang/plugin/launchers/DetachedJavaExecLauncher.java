package org.xtclang.plugin.launchers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.GradleException;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.tasks.XtcLauncherTask;

/**
 * Launcher that runs XTC processes in detached mode using Java execution.
 * The process continues running after Gradle exits.
 */
public class DetachedJavaExecLauncher<E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>>
        extends JavaExecLauncher<E, T> {

    private final DetachedLauncher detachedHelper;

    public DetachedJavaExecLauncher(
            final T task,
            final Logger logger,
            final ExecOperations execOperations,
            final Provider<@NotNull String> toolchainExecutable,
            final Provider<@NotNull String> projectVersion,
            final Provider<@NotNull FileTree> xdkFileTree,
            final Provider<@NotNull FileCollection> javaToolsConfig,
            final File buildDir,
            final File projectDir) {
        super(task, logger, execOperations, toolchainExecutable, projectVersion, xdkFileTree, javaToolsConfig);
        this.detachedHelper = new DetachedLauncher(logger, buildDir, projectDir) {};
    }

    @Override
    public ExecResult apply(final CommandLine cmd) {
        logger.info("[plugin] Launching task in detached mode: {}", this);
        // Resolve javatools.jar using parent's logic
        final File javaToolsJar = resolveJavaTools();
        if (javaToolsJar == null) {
            throw new GradleException("[plugin] Failed to resolve javatools.jar for detached execution.");
        }
        if (task.hasVerboseLogging()) {
            final var launchLine = cmd.toString(javaToolsJar);
            logger.lifecycle("[plugin] Detached JavaExec command: {}", launchLine);
        }
        final String javaExec = toolchainExecutable.getOrElse("java");
        final var command = new ArrayList<>(List.of(javaExec));
        command.addAll(cmd.getJvmArgs());
        command.addAll(List.of("-cp", javaToolsJar.getAbsolutePath(), cmd.getMainClassName()));
        command.addAll(cmd.toList());
        try {
            return detachedHelper.startDetachedProcess(command, cmd.getIdentifier());
        } catch (final IOException e) {
            throw new GradleException("[plugin] Failed to start detached Java process for task: " + this, e);
        }
    }
}
