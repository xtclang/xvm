package org.xtclang.plugin.launchers;

import org.gradle.api.Project;
import org.gradle.api.logging.LogLevel;
import org.gradle.process.ExecResult;
import org.xtclang.plugin.ProjectDelegate;
import org.xtclang.plugin.XtcBuildException;

import static org.xtclang.plugin.Constants.XTC_COMPILER_CLASS_NAME;
import static org.xtclang.plugin.Constants.XTC_COMPILER_LAUNCHER_NAME;
import static org.xtclang.plugin.Constants.XTC_RUNNER_CLASS_NAME;
import static org.xtclang.plugin.Constants.XTC_RUNNER_LAUNCHER_NAME;
import static org.xtclang.plugin.launchers.XtcExecResult.XtcExecResultBuilder;

public abstract class XtcLauncher extends ProjectDelegate<CommandLine, ExecResult> {
    protected XtcLauncher(final Project project, final String description) {
        super(project);
        logger.info("{} (Launcher '{}') spawns '{}'.", prefix(project), JavaExecLauncher.class.getSimpleName(), description);
    }

    private static String nativeLauncherFor(final String mainClassName) {
        return switch (mainClassName) {
            case XTC_COMPILER_CLASS_NAME -> XTC_COMPILER_LAUNCHER_NAME;
            case XTC_RUNNER_CLASS_NAME -> XTC_RUNNER_LAUNCHER_NAME;
            default -> throw new XtcBuildException("Unknown launcher for corresponding class: " + mainClassName);
        };
    }

    public static XtcLauncher create(final Project project, final String mainClassName, final boolean isFork, final boolean isNativeLauncher) {
        if (isNativeLauncher) {
            assert isFork : "For option for native launcher will be ignored. A native process is always forked.";
            project.getLogger().warn("{} The XTC plugin does not yet support using the native launcher.", ProjectDelegate.prefix(project)); // TODO: Verify this works.
            return new NativeBinaryLauncher(project, nativeLauncherFor(mainClassName));
        }
        if (isFork) {
            return new JavaExecLauncher(project, mainClassName);
        }
        return new BuildThreadLauncher(project, mainClassName);
    }

    protected XtcExecResult createExecResult(final XtcExecResultBuilder builder) {
        assert builder.hasExitValue();
        // TODO: System.exit callback if we are running in the builder thread, or things get nasty.
        final XtcExecResult result = builder.build();
        assert result.isSuccessful() || result.getFailure() != null : "Should always have a failure for an XtcExecResult";
        return logOutputs(result);
    }

    public XtcExecResult logOutputs(final XtcExecResult result) {
        if (!result.hasOutputs()) {
            logger.info("{} [stdout | stderr] No output.", prefix);
        }
        logStream("stdout", LogLevel.LIFECYCLE, result.getOutputStdout());
        logStream("stderr", LogLevel.ERROR, result.getOutputStderr());
        return result;
    }

    public void logStream(final String name, final LogLevel level, final String output) {
        if (!output.isEmpty()) {
            output.lines().forEach(line -> logger.log(level, "{} [{}] @ {}", prefix, name, line));
        }
    }
}
