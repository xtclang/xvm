package org.xtclang.plugin.launchers;

import org.gradle.api.Project;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.process.ExecResult;
import org.xtclang.plugin.ProjectDelegate;
import org.xtclang.plugin.XtcBuildException;
import org.xtclang.plugin.XtcTaskExtension;

import static org.gradle.api.logging.LogLevel.ERROR;
import static org.gradle.api.logging.LogLevel.LIFECYCLE;
import static org.xtclang.plugin.XtcPluginConstants.XTC_COMPILER_CLASS_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_COMPILER_LAUNCHER_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_RUNNER_CLASS_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_RUNNER_LAUNCHER_NAME;
import static org.xtclang.plugin.launchers.XtcExecResult.XtcExecResultBuilder;

public abstract class XtcLauncher extends ProjectDelegate<CommandLine, ExecResult> {
    protected final boolean logOutputs;

    protected XtcLauncher(final Project project, final String description, final boolean logOutputs) {
        super(project);
        this.logOutputs = logOutputs;
        logger.info("{} (Launcher '{}', logOutputs={}) spawns '{}'.", prefix(project), JavaExecLauncher.class.getSimpleName(), logOutputs, description);
    }

    private static String nativeLauncherFor(final String mainClassName) {
        return switch (mainClassName) {
            case XTC_COMPILER_CLASS_NAME -> XTC_COMPILER_LAUNCHER_NAME;
            case XTC_RUNNER_CLASS_NAME -> XTC_RUNNER_LAUNCHER_NAME;
            default -> throw new XtcBuildException("Unknown launcher for corresponding class: " + mainClassName);
        };
    }

    public static XtcLauncher create(final Project project, final String mainClassName, final XtcTaskExtension ext) {
        // Note that this does eager evaluations at task creating time, but we don't think the fields queried will
        // ever contain complex logic, and it would likely be just as fine to represent them as booleans only internally.
        // However, we get some free syntactic sugar keeping them as Property<Boolean>.
        final boolean isFork = ext.getFork().get();
        final boolean logOutputs = ext.getLogOutputs().get();
        final Logger logger = project.getLogger();
        final String prefix = ProjectDelegate.prefix(project);
        if (ext.getUseNativeLauncher().get()) {
            assert isFork : "For option for native launcher will be ignored. A native process is always forked.";
            logger.info("{} Created XTC launcher: native executable.", prefix);
            return new NativeBinaryLauncher(project, nativeLauncherFor(mainClassName), logOutputs);
        }
        if (isFork) {
            logger.info("{} Created XTC launcher: Java process forked from build.", prefix);
            return new JavaExecLauncher(project, mainClassName, logOutputs);
        }

        logger.warn("{} Created XTC launcher: Running launcher in the same thread as the build process. This is not recommended for production use.", prefix);
        return new BuildThreadLauncher(project, mainClassName, logOutputs);
    }

    protected XtcExecResult createExecResult(final XtcExecResultBuilder builder) {
        assert builder.hasExitValue();
        // TODO: System.exit callback if we are running in the builder thread, or things get nasty.
        final XtcExecResult result = builder.build();
        assert result.isSuccessful() || result.getFailure() != null : "Should always have a failure for an XtcExecResult";
        return logOutputs(result);
    }

    protected final XtcExecResultBuilder resultBuilder(final CommandLine cmd) {
        return XtcExecResult.builder(getClass(), cmd, logOutputs);
    }

    @SuppressWarnings("UnusedReturnValue")
    protected boolean validateCommandLine(final CommandLine cmd) {
        return true;
    }

    public XtcExecResult logOutputs(final XtcExecResult result) {
        if (!logOutputs) {
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
