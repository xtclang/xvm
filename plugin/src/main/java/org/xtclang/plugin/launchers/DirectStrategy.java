package org.xtclang.plugin.launchers;

import java.util.List;

import org.gradle.api.logging.Logger;

import org.xtclang.plugin.XtcRunModule;
import org.xtclang.plugin.runtime.DirectCompileRequest;
import org.xtclang.plugin.runtime.DirectRunRequest;
import org.xtclang.plugin.runtime.DirectRuntimeInvoker;
import org.xtclang.plugin.runtime.DirectTestRequest;
import org.xtclang.plugin.tasks.XtcCompileTask;
import org.xtclang.plugin.tasks.XtcRunTask;
import org.xtclang.plugin.tasks.XtcTestTask;

import static org.xtclang.plugin.tasks.XtcLauncherTask.EXIT_CODE_ERROR;

/**
 * Direct (in-process) execution strategy.
 * Calls Launcher.launch() directly with pre-built options.
 * Works for both compile and run tasks.
 */
public class DirectStrategy implements ExecutionStrategy {
    private static final String DEFAULT_METHOD_NAME = "run";

    private final Logger logger;

    public DirectStrategy(final Logger logger) {
        this.logger = logger;
    }

    @Override
    public ExecutionMode getMode() {
        return ExecutionMode.DIRECT;
    }

    @Override
    public int execute(final XtcCompileTask task) {
        logger.info("[plugin] Invoking compiler directly in current thread (no fork)");
        try {
            return DirectRuntimeInvoker.executeCompile(task.resolveLauncherRuntime(), createCompileRequest(task), logger);
        } catch (final Exception e) {
            logger.error("[plugin] Direct compiler execution failed", e);
            return EXIT_CODE_ERROR;
        }
    }

    @Override
    public int execute(final XtcRunTask task, final XtcRunModule runConfig) {
        logger.info("[plugin] Invoking runner directly in current thread (no fork)");
        try {
            return DirectRuntimeInvoker.executeRun(task.resolveLauncherRuntime(), createRunRequest(task, runConfig), logger);
        } catch (final Exception e) {
            logger.error("[plugin] Direct runner execution failed", e);
            return EXIT_CODE_ERROR;
        }
    }

    @Override
    public int execute(final XtcTestTask task, final XtcRunModule runConfig) {
        logger.info("[plugin] Invoking test runner directly in current thread (no fork)");
        try {
            return DirectRuntimeInvoker.executeTest(task.resolveLauncherRuntime(), createTestRequest(task, runConfig), logger);
        } catch (final Exception e) {
            logger.error("[plugin] Direct test runner execution failed", e);
            return EXIT_CODE_ERROR;
        }
    }

    private static DirectCompileRequest createCompileRequest(final XtcCompileTask task) {
        final var rawVersion = task.getXtcVersion().getOrNull();
        final String semanticVersion = rawVersion == null || rawVersion.isBlank()
            ? null
            : XtcCompileTask.semanticVersion(rawVersion);
        return new DirectCompileRequest(
            task.getProjectDirectory().get().getAsFile(),
            task.getOutputDirectoryInternal().getAsFile(),
            task.getResourceDirectoryInternal().getAsFile(),
            task.resolveFullModulePath(),
            List.copyOf(task.resolveXtcSourceFiles()),
            task.getRebuild().get(),
            task.getShowVersion().get(),
            task.getVerbose().get(),
            task.getDisableWarnings().get(),
            task.getStrict().get(),
            task.getQualifiedOutputName().get(),
            semanticVersion
        );
    }

    private static DirectRunRequest createRunRequest(final XtcRunTask task, final XtcRunModule runConfig) {
        return new DirectRunRequest(
            task.getProjectDirectory().get().getAsFile(),
            task.resolveFullModulePath(),
            task.getShowVersion().get(),
            task.getVerbose().get(),
            runConfig.getModuleName().get(),
            task.getMethodName().getOrElse(DEFAULT_METHOD_NAME),
            runConfig.getModuleArgs().get()
        );
    }

    private static DirectTestRequest createTestRequest(final XtcTestTask task, final XtcRunModule runConfig) {
        return new DirectTestRequest(
            task.getProjectDirectory().get().getAsFile(),
            task.getOutputDirectory().get().getAsFile(),
            task.resolveFullModulePath(),
            task.getShowVersion().get(),
            task.getVerbose().get(),
            runConfig.getModuleName().get(),
            task.getMethodName().getOrElse(DEFAULT_METHOD_NAME),
            runConfig.getModuleArgs().get()
        );
    }
}
