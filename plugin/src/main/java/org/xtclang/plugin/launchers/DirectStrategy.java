package org.xtclang.plugin.launchers;

import java.util.ServiceLoader;

import org.gradle.api.logging.Logger;

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
public class DirectStrategy<T extends XtcLauncherTask<?>> implements ExecutionStrategy<T> {

    private final Logger logger;
    private final Console console;
    private final ErrorListener errorListener;

    public DirectStrategy(final Logger logger, final Console console, final ErrorListener errorListener) {
        this.logger = logger;
        this.console = console;
        this.errorListener = errorListener;
    }

    @Override
    public int execute(final XtcCompileTask task) {
        logger.info("[plugin] Invoking compiler directly in current thread (no fork)");

        try {
            // Use ServiceLoader - javatools already loaded by XtcLoadJavaToolsTask
            final ServiceLoader<Launchable> loader = ServiceLoader.load(Launchable.class);
            final Launchable launcher = loader.iterator().next();

            final var options = LauncherOptionsBuilder.buildCompilerOptions(task);

            return launcher.launch(options, console, errorListener);

        } catch (final Exception e) {
            logger.error("[plugin] Direct compiler execution failed", e);
            return -1;
        }
    }

    @Override
    public int execute(final XtcRunTask task, final XtcRunModule runConfig) {
        logger.info("[plugin] Invoking runner directly in current thread (no fork)");

        try {
            // Use ServiceLoader - javatools already loaded by XtcLoadJavaToolsTask
            final ServiceLoader<Launchable> loader = ServiceLoader.load(Launchable.class);
            final Launchable launcher = loader.iterator().next();

            // Extract module info from runConfig
            final String moduleName = runConfig.getModuleName().get();
            final String[] moduleArgs = runConfig.getModuleArgs().get().toArray(String[]::new);

            final var options = LauncherOptionsBuilder.buildRunnerOptions(task, moduleName, moduleArgs);

            return launcher.launch(options, console, errorListener);

        } catch (final Exception e) {
            logger.error("[plugin] Direct runner execution failed", e);
            return -1;
        }
    }
}
