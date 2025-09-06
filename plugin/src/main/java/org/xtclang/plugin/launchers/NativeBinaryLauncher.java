package org.xtclang.plugin.launchers;

import java.io.File;

import java.util.Objects;
import java.util.StringTokenizer;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;

import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.internal.DefaultXtcLauncherTaskExtension;
import org.xtclang.plugin.tasks.XtcLauncherTask;

@SuppressWarnings("unused")
public class NativeBinaryLauncher<E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>> extends XtcLauncher<E, T> {

    private final String commandName;
    private final ExecOperations execOperations;

    public NativeBinaryLauncher(final Project project, final T task, final ExecOperations execOperations) {
        super(project, task);
        this.commandName = task.getNativeLauncherCommandName();
        this.execOperations = execOperations;
    }
    
    public NativeBinaryLauncher(final T task, final Logger logger, final ExecOperations execOperations) {
        super(task, logger);
        this.commandName = task.getNativeLauncherCommandName();
        this.execOperations = execOperations;
    }

    @Override
    protected boolean validateCommandLine(final CommandLine cmd) {
        final var mainClassName = cmd.getMainClassName();
        final var jvmArgs = cmd.getJvmArgs();
        if (DefaultXtcLauncherTaskExtension.hasModifiedJvmArgs(jvmArgs)) {
            logger.warn("[plugin] WARNING: Launcher for mainClassName '{}' has non-default JVM args ({}). These are ignored, as we are running a native launcher.",
                mainClassName, jvmArgs);
        }
        return super.validateCommandLine(cmd);
    }

    private File findOnPath(final String commandName) {
        final var path = Objects.requireNonNull(System.getenv("PATH"));
        final var st = new StringTokenizer(path, File.pathSeparator);
        while (st.hasMoreTokens()) {
            final var cmd = new File(st.nextToken(), commandName);
            if (cmd.exists() && cmd.canExecute()) {
                logger.info("[plugin] Successfully resolved path for command '{}': '{}'", commandName, cmd.getAbsolutePath());
                return cmd;
            }
        }
        throw new GradleException("[plugin] Could not resolve " + commandName + " from system path: " + path);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ExecResult apply(final CommandLine cmd) {
        logger.info("[plugin] Launching task: {}}", this);
        validateCommandLine(cmd);
        if (task.hasVerboseLogging()) {
            logger.lifecycle("[plugin] NativeExec command: {}", cmd.toString());
        }
        final var builder = resultBuilder(cmd);
        final var execResult = execOperations.exec(spec -> {
            redirectIo(builder, spec);
            spec.setExecutable(findOnPath(commandName));
            spec.setArgs(cmd.toList());
            spec.setIgnoreExitValue(true);
        });
        return createExecResult(builder.execResult(execResult));
    }
}
