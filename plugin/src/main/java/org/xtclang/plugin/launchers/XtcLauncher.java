package org.xtclang.plugin.launchers;

import org.gradle.api.Project;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.process.BaseExecSpec;
import org.gradle.process.ExecResult;
import org.xtclang.plugin.ProjectDelegate;
import org.xtclang.plugin.XtcBuildException;
import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.tasks.XtcLauncherTask;

import static org.gradle.api.logging.LogLevel.ERROR;
import static org.gradle.api.logging.LogLevel.LIFECYCLE;
import static org.xtclang.plugin.XtcPluginConstants.XTC_COMPILER_CLASS_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_COMPILER_LAUNCHER_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_RUNNER_CLASS_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_RUNNER_LAUNCHER_NAME;
import static org.xtclang.plugin.launchers.XtcExecResult.XtcExecResultBuilder;

public abstract class XtcLauncher<E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>> extends ProjectDelegate<CommandLine, ExecResult> {
    protected final T task;

    protected XtcLauncher(final Project project, final String description, final T task) {
        super(project);
        this.task = task;
    }

    private static String nativeLauncherFor(final String mainClassName) {
        return switch (mainClassName) {
            case XTC_COMPILER_CLASS_NAME -> XTC_COMPILER_LAUNCHER_NAME;
            case XTC_RUNNER_CLASS_NAME -> XTC_RUNNER_LAUNCHER_NAME;
            default -> throw new XtcBuildException("Unknown launcher for corresponding class: " + mainClassName);
        };
    }

    protected boolean outputStreamsToLog() {
        return outputStreamsToLog(task);
    }

    protected boolean isFork() {
        return isFork(task);
    }

    protected boolean isNativeLauncher() {
        return isNativeLauncher(task);
    }

    protected boolean redirectStdin() {
        return task.getStdin().isPresent();
    }

    protected boolean redirectStdout() {
        return task.getStdout().isPresent();
    }

    protected boolean redirectStderr() {
        return task.getStderr().isPresent();
    }

    protected boolean redirectAnyOutput() {
        return redirectStdout() || redirectStderr();
    }

    protected void redirectIo(final XtcExecResultBuilder builder, final BaseExecSpec spec) {
        if (outputStreamsToLog()) {
            spec.setStandardOutput(builder.getOut());
            spec.setErrorOutput(builder.getErr());
            if (redirectAnyOutput()) {
                warn("{} WARNING: Task '{}' is already configured to override stdout and/or stderr. It may cause problems to redirect them to the build log.", prefix, task.getName());
            }
        }

        if (redirectStdin()) {
            spec.setStandardInput(task.getStdin().get());
        }

        if (redirectStdout()) {
            spec.setStandardOutput(task.getStdout().get());
        }

        if (redirectStderr()) {
            spec.setErrorOutput(task.getStderr().get());
        }
    }

    private static <E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>> boolean outputStreamsToLog(final T task) {
        return task.getLogOutputs().get();
    }

    private static <E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>> boolean isFork(final T task) {
        return task.getFork().get();
    }

    private static <E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>> boolean isNativeLauncher(final T task) {
        return task.getUseNativeLauncher().get();
    }

    public static <E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>> XtcLauncher<E, T> create(final Project project, final String mainClassName, final T task) {
        // Note that this does eager evaluations at task creating time, but we don't think the fields queried will
        // ever contain complex logic, and it would likely be just as fine to represent them as booleans only internally.
        // However, we get some free syntactic sugar keeping them as Property<Boolean>.
        final Logger logger = project.getLogger();
        final String prefix = ProjectDelegate.prefix(project);

        if (isNativeLauncher(task)) {
            assert isFork(task) : "For option for native launcher will be ignored. A native process is always forked.";
            logger.info("{} Created XTC launcher: native executable.", prefix);
            return new NativeBinaryLauncher<>(project, nativeLauncherFor(mainClassName), task);
        }

        if (isFork(task)) {
            logger.info("{} Created XTC launcher: Java process forked from build.", prefix);
            return new JavaExecLauncher<>(project, mainClassName, task);
        }

        logger.warn("{} Created XTC launcher: Running launcher in the same thread as the build process. This is not recommended for production use.", prefix);
        return new BuildThreadLauncher<>(project, mainClassName, task);
    }

    protected XtcExecResult createExecResult(final XtcExecResultBuilder builder) {
        assert builder.hasExitValue();
        // TODO: System.exit callback if we are running in the builder thread, or things get nasty.
        final XtcExecResult result = builder.build();
        assert result.isSuccessful() || result.getFailure() != null : "Should always have a failure for an XtcExecResult";
        return logOutputs(result);
    }

    protected final XtcExecResultBuilder resultBuilder(final CommandLine cmd) {
        return XtcExecResult.builder(getClass(), cmd, task.getLogOutputs().get());
    }

    @SuppressWarnings("UnusedReturnValue")
    protected boolean validateCommandLine(final CommandLine cmd) {
        return true;
    }

    public XtcExecResult logOutputs(final XtcExecResult result) {
        if (!task.getLogOutputs().get()) {
            logger.lifecycle("{} Exec outputs were sent to stdout/stderr.", prefix);
            return result;
        }
        if (!result.hasOutputs()) {
            logger.info("{} [stdout | stderr] No output.", prefix);
        }
        logStream("stdout", LIFECYCLE, result.getOutputStdout());
        logStream("stderr", ERROR, result.getOutputStderr());
        return result;
    }

    public void logStream(final String name, final LogLevel level, final String output) {
        if (!output.isEmpty()) {
            output.lines().forEach(line -> logger.log(level, "{} [{}] @ {}", prefix, name, line));
        }
    }
}
