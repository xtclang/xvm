package org.xtclang.plugin.runtime.impl;

import org.gradle.api.logging.Logger;

import org.xvm.asm.ErrorList;
import org.xvm.tool.Console;
import org.xvm.tool.Launcher;
import org.xvm.tool.Launcher.LauncherException;
import org.xvm.tool.Runner;
import org.xvm.tool.TestRunner;
import org.xvm.util.Severity;

import org.xtclang.plugin.runtime.DirectCompileRequest;
import org.xtclang.plugin.runtime.DirectRunRequest;
import org.xtclang.plugin.runtime.DirectTestRequest;

/**
 * These entry points are loaded inside {@link org.xtclang.plugin.runtime.PluginRuntimeClassLoader}.
 * That is why this class is allowed to use the real javatools APIs directly while the outer
 * Gradle task classes are not.
 */
public final class IsolatedDirectExecutor {
    private static final int DEFAULT_ERROR_LIMIT = 100;

    private IsolatedDirectExecutor() {
    }

    /**
     * Returns the library modules that the compiles of one build share, for {@link #executeCompile}.
     *
     * @return the shared library modules, typed for the caller, whose classloader does not know their class
     */
    public static Object newLibraryModules() {
        return new LibraryModules();
    }

    /**
     * Compiles as the command line does, with {@code Launcher.launch}'s handling of failures, but against the library
     * modules that the earlier compiles of this build loaded. The compiles that share them run one at a time.
     *
     * @param request         the compile
     * @param logger          the logger of the task
     * @param libraryModules  what {@link #newLibraryModules()} returned
     * @return the exit code
     */
    public static int executeCompile(final DirectCompileRequest request, final Logger logger, final Object libraryModules) {
        final var libraries = (LibraryModules) libraryModules;
        final var err = new ErrorList(DEFAULT_ERROR_LIMIT);
        final var console = createConsole(logger);
        final var options = new IsolatedLauncherOptionsBuilder().buildCompilerOptions(request);
        synchronized (libraries) {
            final int fileReads = libraries.fileReads();
            final int reuses = libraries.reuses();
            final int exitCode = launch(new SharedLibraryCompiler(options, console, err, libraries), console);
            logger.info("[plugin] [DIRECT] Library module files: {} read, {} reused from earlier compiles of this build",
                libraries.fileReads() - fileReads, libraries.reuses() - reuses);
            return exitCode;
        }
    }

    // As Launcher.launch() runs a launcher
    private static int launch(final Launcher<?> launcher, final Console console) {
        try {
            return launcher.run();
        } catch (final LauncherException e) {
            if (e.isError()) {
                console.log(Severity.ERROR, e.getMessage());
            }
            return e.getExitCode();
        } catch (final Throwable e) {
            console.log(Severity.ERROR, e, "Unhandled exception");
            return 1;
        }
    }

    public static int executeRun(final DirectRunRequest request, final Logger logger) {
        final var err = new ErrorList(DEFAULT_ERROR_LIMIT);
        final var console = createConsole(logger);
        final var options = new IsolatedLauncherOptionsBuilder().buildRunnerOptions(request);
        return new Runner(options, console, err).run();
    }

    public static int executeTest(final DirectTestRequest request, final Logger logger) {
        final var err = new ErrorList(DEFAULT_ERROR_LIMIT);
        final var console = createConsole(logger);
        final var options = new IsolatedLauncherOptionsBuilder().buildTestRunnerOptions(request);
        return new TestRunner(options, console, err).run();
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
}
