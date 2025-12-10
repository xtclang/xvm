package org.xtclang.plugin.launchers;

import org.gradle.api.logging.Logger;

import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.tool.Console;
import org.xvm.tool.Launcher;
import org.xvm.tool.Runner;
import org.xvm.tool.TestRunner;
import org.xvm.util.Severity;

import org.xtclang.plugin.XtcRunModule;
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

    private final Logger logger;
    private final Console console;
    private final ErrorListener err;

    public DirectStrategy(final Logger logger) {
        this.logger = logger;
        this.console = createConsole(logger);
        this.err = new ErrorList(100);
    }

    private static Console createConsole(final Logger logger) {
        return new Console() {
            @Override
            public String log(final Severity severity, final String template, final Object... params) {
                final String message = Console.formatTemplate(template, params);
                switch (severity) {
                    case ERROR, FATAL -> logger.error(message);
                    case WARNING -> logger.warn(message);
                    case INFO -> logger.lifecycle(message);
                    default -> logger.info(message);
                }
                return message;
            }
        };
    }

    @Override
    public ExecutionMode getMode() {
        return ExecutionMode.DIRECT;
    }

    protected LauncherOptionsBuilder optionsBuilder() {
        return new LauncherOptionsBuilder(getMode());
    }

    @Override
    public int execute(final XtcCompileTask task) {
        logger.info("[plugin] Invoking compiler directly in current thread (no fork)");
        try {
            final var options = optionsBuilder().buildCompilerOptions(task);
            final int exitCode = Launcher.launch(options, console, err);
            logger.lifecycle("Finished calling xcc; {})", err);
            return exitCode;
        } catch (final Exception e) {
            logger.error("[plugin] Direct compiler execution failed", e);
            return EXIT_CODE_ERROR;
        }
    }

    @Override
    public int execute(final XtcRunTask task, final XtcRunModule runConfig) {
        final boolean isTest = task instanceof XtcTestTask;
        logger.info("[plugin] Invoking {} directly in current thread (no fork)", isTest ? "test runner" : "runner");
        try {
            final String moduleName = runConfig.getModuleName().get();
            final var moduleArgs = runConfig.getModuleArgs().get();
            final var optBuilder = optionsBuilder();
            // Instantiate the appropriate launcher based on task type
            final Launcher<?> launcher = isTest
                    ? new TestRunner(optBuilder.buildTestRunnerOptions(task, moduleName, moduleArgs), console, err)
                    : new Runner(optBuilder.buildRunnerOptions(task, moduleName, moduleArgs), console, err);
            return launcher.run();
        } catch (final Launcher.LauncherException e) {
            logger.error("[plugin] Direct {} execution failed: {}", isTest ? "test runner" : "runner", e.getMessage());
            return e.getExitCode();
        } catch (final Exception e) {
            logger.error("[plugin] Direct {} execution failed", isTest ? "test runner" : "runner", e);
            return EXIT_CODE_ERROR;
        }
    }
}
