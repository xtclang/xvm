package org.xtclang.plugin.launchers;

import java.util.List;
import java.util.ServiceLoader;

import org.gradle.api.logging.Logger;

import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.tool.Console;
import org.xvm.tool.Launchable;

import org.xtclang.plugin.XtcRunModule;
import org.xtclang.plugin.tasks.XtcCompileTask;
import org.xtclang.plugin.tasks.XtcLauncherTask;
import org.xtclang.plugin.tasks.XtcRunTask;

/**
 * Direct (in-process) execution strategy.
 * Uses javatools classes already loaded by XtcLoadJavaToolsTask via ServiceLoader.
 * Works for both compile and run tasks.
 */
public class DirectStrategy<T extends XtcLauncherTask<?>> implements ExecutionStrategy {

    private final Logger logger;
    private final Console console;
    private final ErrorListener err;

    public DirectStrategy(final Logger logger) {
        this.logger = logger;
        this.console = createConsole(logger);
        this.err = new ErrorList();
    }

    private static Console createConsole(final Logger logger) {
        return new Console() {
            @Override
            public String log(final org.xvm.util.Severity severity, final String template, final Object... params) {
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
            // Use ServiceLoader - javatools already loaded by XtcLoadJavaToolsTask
            final ServiceLoader<Launchable> loader = ServiceLoader.load(Launchable.class);
            final Launchable launcher = loader.iterator().next();
            // Use absolute paths for DIRECT mode since we can't control the working directory
            final var options = optionsBuilder().buildCompilerOptions(task);
            final int exitCode = launcher.launch(options, console, err);
            logger.lifecycle("Finished calling xcc; {})", err);
            return exitCode;
        } catch (final Exception e) {
            logger.error("[plugin] Direct compiler execution failed", e);
            return -1;
        }
    }

    @Override
    public int execute(final XtcRunTask task, final XtcRunModule runConfig) {
        logger.info("[plugin] Invoking runner directly in current thread (no fork)");
        // This runs in task execution time, and hence it is fine to work with the javatools
        // classses. compileApi to javatools makes it possible to refer to them anywhere, but
        // unless javatools.jar has been loaded and we are at task execution time for any task
        // we cannot execute code with the types, though.
        try {
            // Use ServiceLoader - javatools already loaded by XtcLoadJavaToolsTask
            final ServiceLoader<Launchable> loader = ServiceLoader.load(Launchable.class);
            final Launchable launcher = loader.iterator().next();
            // Extract module info from runConfig
            final String moduleName = runConfig.getModuleName().get();
            final var moduleArgs = runConfig.getModuleArgs().get();
            // Use absolute paths for DIRECT mode since we can't control the working directory
            final var options = optionsBuilder().buildRunnerOptions(task, moduleName, moduleArgs);
            return launcher.launch(options, console, err);
        } catch (final Exception e) {
            logger.error("[plugin] Direct runner execution failed", e);
            return -1;
        }
    }
}
