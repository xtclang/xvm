package org.xtclang.plugin.launchers;

import org.gradle.api.Project;
import org.gradle.process.ExecResult;
import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.internal.DefaultXtcLauncherTaskExtension;
import org.xtclang.plugin.tasks.XtcLauncherTask;

import java.io.File;
import java.util.Objects;
import java.util.StringTokenizer;

@SuppressWarnings("unused")
public class NativeBinaryLauncher<E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>> extends XtcLauncher<E, T> {

    private final String commandName;

    public NativeBinaryLauncher(final Project project, final T task) {
        super(project, task);
        this.commandName = task.getNativeLauncherCommandName();
    }

    @Override
    protected boolean validateCommandLine(final CommandLine cmd) {
        final var mainClassName = cmd.getMainClassName();
        final var jvmArgs = cmd.getJvmArgs();
        if (DefaultXtcLauncherTaskExtension.hasModifiedJvmArgs(jvmArgs)) {
            logger.warn("{} WARNING: Launcher for mainClassName '{}' has non-default JVM args ({}). These will be ignored, as we are running a native launcher.", prefix, mainClassName, jvmArgs);
        }
        return super.validateCommandLine(cmd);
    }

    private File findOnPath(final String commandName) {
        final var path = Objects.requireNonNull(System.getenv("PATH"));
        final var st = new StringTokenizer(path, File.pathSeparator);
        while (st.hasMoreTokens()) {
            final var cmd = new File(st.nextToken(), commandName);
            if (cmd.exists() && cmd.canExecute()) {
                logger.info("{} Successfully resolved path for command '{}': '{}'", prefix, commandName, cmd.getAbsolutePath());
                return cmd;
            }
        }
        throw buildException("Could not resolve " + commandName + " from system path: " + path);
    }

    @Override
    public ExecResult apply(final CommandLine cmd) {
        logger.info("{} Launching task: {}}", prefix, this);
        validateCommandLine(cmd);
        if (task.hasVerboseLogging()) {
            logger.lifecycle("{} NativeExec command: {}", prefix, cmd.toString());
        }
        final var builder = resultBuilder(cmd);
        return createExecResult(builder.execResult(project.exec(spec -> {
            redirectIo(builder, spec);
            spec.setExecutable(findOnPath(commandName));
            spec.setArgs(cmd.toList());
            spec.setIgnoreExitValue(true);
        })));
    }
}
