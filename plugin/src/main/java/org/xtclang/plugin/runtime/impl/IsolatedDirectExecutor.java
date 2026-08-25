package org.xtclang.plugin.runtime.impl;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

import org.gradle.api.logging.Logger;

import org.xvm.asm.ErrorList;
import org.xvm.runtime.Container;
import org.xvm.runtime.OwnershipDiagnostics;
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
    private static final int DEFAULT_OWNERSHIP_VALIDATION_WINDOW = 6;
    private static final String OWNERSHIP_VALIDATION_WINDOW_PROPERTY =
        "org.xtclang.directRuntimeOwnershipWindow";

    /**
     * Containers observed by opt-in direct same-JVM ownership validation.
     * This class is loaded by the build-scoped direct runtime classloader, so
     * the list naturally has the same lifetime as the reused direct runtime.
     *
     * The stress validator keeps only a recent window. The current container is
     * always validated, so stale owner values reachable from the current run are
     * still rejected; the window catches direct cross-run sharing without making
     * the diagnostic harness retain every completed runtime graph in a long
     * all-module stress run.
     */
    private static final Deque<Container> observedContainers = new ArrayDeque<>();

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
        final var runner = new Runner(options, console, err);
        final int result = runner.run();
        if (result == 0 && request.validateRuntimeOwnership()) {
            validateRuntimeOwnership(request.moduleName(), runner, logger);
        }
        return result;
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

    private static void validateRuntimeOwnership(final String moduleName, final Runner runner, final Logger logger) {
        final Container container = runner.diagnosticContainer();
        if (container == null) {
            throw new IllegalStateException(
                "Direct runtime ownership validation requires an interpreter container; module=" + moduleName);
        }

        final Container[] containers;
        synchronized (observedContainers) {
            observedContainers.add(container);
            while (observedContainers.size() > ownershipValidationWindow()) {
                observedContainers.removeFirst();
            }

            final Set<Container> relatedContainers = new LinkedHashSet<>(observedContainers);
            relatedContainers.addAll(OwnershipDiagnostics.runtimeContainers(container));
            containers = relatedContainers.toArray(Container[]::new);
        }

        try {
            OwnershipDiagnostics.assertValid(containers);
        } catch (final IllegalStateException e) {
            logger.error("Runtime ownership validation failed after module {}", moduleName);
            logger.error("{}", OwnershipDiagnostics.dump(containers));
            throw e;
        }

        // the curated validation above checks the state the walkers know about; the generic
        // reachability sweep checks EVERYTHING reachable, and across the window it is the direct
        // cross-run detector: a completed earlier run's container is unrelated to the current
        // one, so any retained reference between their graphs is flagged with a path-to-root.
        // A sweep is only accepted when it is complete - blind spots fail the run too, so the
        // stress verdict can never silently narrow.
        for (final Container observed : containers) {
            final var report = OwnershipDiagnostics.sweepForeignReferences(observed);
            if (report.isClean()) {
                logger.info("Reachability sweep clean after module {}: {}",
                    moduleName, report.render());
            } else {
                logger.error("Reachability sweep failed after module {}", moduleName);
                logger.error("{}", report.render());
                throw new IllegalStateException(
                    "reachability sweep after module " + moduleName + ": " + report.render());
            }
        }
    }

    private static int ownershipValidationWindow() {
        return Math.max(1, Integer.getInteger(
            OWNERSHIP_VALIDATION_WINDOW_PROPERTY,
            DEFAULT_OWNERSHIP_VALIDATION_WINDOW));
    }
}
