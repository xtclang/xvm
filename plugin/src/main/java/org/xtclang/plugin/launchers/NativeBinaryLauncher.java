package org.xtclang.plugin.launchers;

import org.gradle.api.logging.Logger;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.internal.DefaultXtcLauncherTaskExtension;
import org.xtclang.plugin.tasks.XtcLauncherTask;

public class NativeBinaryLauncher<E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>> extends XtcLauncher<E, T> {

    protected final String commandName;
    private final ExecOperations execOperations;

    public NativeBinaryLauncher(final T task, final Logger logger, final ExecOperations execOperations) {
        super(task, logger);
        this.commandName = task.getNativeLauncherCommandName();
        this.execOperations = execOperations;
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
}
