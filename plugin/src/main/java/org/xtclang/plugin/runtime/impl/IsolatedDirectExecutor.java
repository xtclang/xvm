package org.xtclang.plugin.runtime.impl;

import org.gradle.api.logging.Logger;

import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.tool.Console;
import org.xvm.tool.Launcher;
import org.xvm.tool.Runner;
import org.xvm.tool.TestRunner;
import org.xvm.util.Severity;

import org.xtclang.plugin.runtime.DirectCompileRequest;
import org.xtclang.plugin.runtime.DirectRunRequest;
import org.xtclang.plugin.runtime.DirectTestRequest;

public final class IsolatedDirectExecutor {
    private static final int DEFAULT_ERROR_LIMIT = 100;

    private IsolatedDirectExecutor() {
    }

    /**
     * These entry points are loaded inside {@link org.xtclang.plugin.runtime.PluginRuntimeClassLoader}.
     * That is why this class is allowed to use the real javatools APIs directly while the outer
     * Gradle task classes are not.
     */
    public static int executeCompile(final DirectCompileRequest request, final Logger logger) {
        final var err = new ErrorList(DEFAULT_ERROR_LIMIT);
        final var console = createConsole(logger);
        final var options = new IsolatedLauncherOptionsBuilder().buildCompilerOptions(request);
        return Launcher.launch(options, console, err);
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
