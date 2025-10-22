package org.xtclang.plugin.launchers;

import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;

import org.gradle.api.logging.Logger;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.tasks.XtcLauncherTask;

/**
 * Launcher that runs XTC processes in detached mode using native executables.
 * The process continues running after Gradle exits.
 */
public class DetachedNativeBinaryLauncher<E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>>
        extends NativeBinaryLauncher<E, T> {

     private final DetachedLauncher detachedHelper;

    public DetachedNativeBinaryLauncher(
            final T task,
            final Logger logger,
            final ExecOperations execOperations,
            final File buildDir,
            final File projectDir) {
        super(task, logger, execOperations);
        this.detachedHelper = new DetachedLauncher(logger, buildDir, projectDir) {};
    }

    @Override
    public ExecResult apply(final CommandLine cmd) {
        logger.info("[plugin] Launching task in detached mode: {}", this);
        validateCommandLine(cmd);
        if (task.hasVerboseLogging()) {
            logger.lifecycle("[plugin] Detached NativeExec command: {}", cmd.toString());
        }
        final var command = Stream.concat(Stream.of(commandName), cmd.toList().stream()).toList();
        try {
            return detachedHelper.startDetachedProcess(command, cmd.getIdentifier());
        } catch (final IOException e) {
            throw new RuntimeException("[plugin] Failed to start detached native process for task: " + this, e);
        }
    }
}
