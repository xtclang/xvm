package org.xtclang.plugin.runtime.impl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;

import org.gradle.api.logging.Logger;

import org.xvm.api.XtcEngine;
import org.xvm.asm.ErrorList;
import org.xvm.asm.Version;
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
    private static final String ENGINE_PROPERTY = "org.xtclang.directRuntimeEngine";
    private static final String DEFAULT_METHOD_NAME = "run";

    /**
     * Engines keyed by module path. Like {@link #observedContainers}, this class is loaded by the
     * build-scoped direct runtime classloader, so an engine - and therefore its booted runtime and
     * native container plane - lives exactly as long as the reused direct runtime does. That is the
     * whole point: the plane stays warm across run tasks instead of being rebuilt per module.
     */
    private static final Map<List<File>, XtcEngine> engines = new HashMap<>();

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
        if (useEngineForCompile(request, logger)) {
            return executeCompileWithEngine(request, logger);
        }
        final var err = new ErrorList(DEFAULT_ERROR_LIMIT);
        final var console = createConsole(logger);
        final var options = new IsolatedLauncherOptionsBuilder().buildCompilerOptions(request);
        return Launcher.launch(options, console, err);
    }

    /**
     * Whether this compile goes through {@link XtcEngine} rather than a fresh {@link Launcher} run.
     *
     * Beyond keeping the engine warm, this matters because {@code Launcher.launch} is a CLI entry
     * point being re-entered repeatedly inside one long-lived classloader, which is not what it was
     * built for. The engine compiles through the compiler stages directly.
     *
     * The engine models sources, resources and version stamping. It does not model qualified output
     * names or strict mode, so a request asking for either still takes the launcher path rather than
     * being silently compiled under different rules.
     */
    private static boolean useEngineForCompile(final DirectCompileRequest request, final Logger logger) {
        if (!Boolean.parseBoolean(System.getProperty(ENGINE_PROPERTY, "true"))) {
            return false;
        }
        final String sReason = engineCompileBlocker(request);
        if (sReason != null) {
            // never fall back silently: a compile that quietly took different rules than the one
            // beside it is worse than a slow compile
            logger.info("[plugin] [DIRECT] Launcher path for this compile: {}", sReason);
            return false;
        }
        return true;
    }

    /**
     * @return why this compile cannot go through the engine, or null if it can
     */
    private static String engineCompileBlocker(final DirectCompileRequest request) {
        if (request.sourceFiles().isEmpty()) {
            return "no source files";
        }
        if (request.qualifiedOutputName()) {
            return "qualified output names are not modelled by the engine";
        }
        if (request.strict()) {
            return "strict mode is not modelled by the engine";
        }
        if (request.modulePath().stream().noneMatch(File::isDirectory)) {
            // The bootstrap case: lib_ecstasy compiles against nothing, so its module path is
            // legitimately empty. An engine cannot serve it at all - constructing one boots a
            // NativeContainer, which needs the very system modules this compile is producing - so
            // this is a permanent exclusion rather than a gap to close. Without it the engine
            // throws "no valid module-path directories were provided" and fails the build.
            return "no module path: the bootstrap module cannot boot an engine";
        }
        return null;
    }

    private static int executeCompileWithEngine(final DirectCompileRequest request, final Logger logger) {
        final var engine = engine(request.modulePath(), logger);
        final File dirResource = request.resourceDir();
        final boolean fResources = dirResource != null && dirResource.isDirectory();
        final XtcEngine.ModuleSource[] aSource = request.sourceFiles().stream()
            .map(file -> fResources
                ? XtcEngine.ModuleSource.of(file.toPath(), dirResource)
                : XtcEngine.ModuleSource.of(file.toPath()))
            .toArray(XtcEngine.ModuleSource[]::new);

        logger.info("[plugin] [DIRECT] Compiling {} module source(s) on the warm engine{}",
            aSource.length, fResources ? " (resources: " + dirResource + ')' : "");

        final XtcEngine.CompileResult result;
        try {
            result = engine.compile(aSource);
        } catch (final RuntimeException e) {
            logger.error("Compilation failed: {}", e.toString());
            return 1;
        }

        for (final var diagnostic : result.diagnostics()) {
            final String sText = diagnostic.code() + ": " + diagnostic.message()
                + (diagnostic.source() == null ? "" : " (" + diagnostic.source() + ':' + diagnostic.line() + ')');
            switch (diagnostic.severity()) {
                case ERROR, FATAL -> logger.error(sText);
                case WARNING -> {
                    if (!request.disableWarnings()) {
                        logger.warn(sText);
                    }
                }
                default -> logger.info(sText);
            }
        }

        if (!result.isSuccess()) {
            return 1;
        }

        Version version = null;
        if (request.xtcVersion() != null && !request.xtcVersion().isBlank()) {
            try {
                version = new Version(request.xtcVersion());
            } catch (final RuntimeException e) {
                logger.error("Unusable module version {}: {}", request.xtcVersion(), e.toString());
                return 1;
            }
        }

        try {
            final var listWritten = result.writeTo(request.outputDir(), version);
            logger.info("[plugin] [DIRECT] Wrote {} module(s) to {}",
                listWritten.size(), request.outputDir());
        } catch (final IOException e) {
            logger.error("Failed to write compiled modules to {}: {}", request.outputDir(), e.toString());
            return 1;
        }
        return 0;
    }

    public static int executeRun(final DirectRunRequest request, final Logger logger) {
        if (useEngine(request)) {
            return executeRunWithEngine(request, logger);
        }
        final var err = new ErrorList(DEFAULT_ERROR_LIMIT);
        final var console = createConsole(logger);
        final var options = new IsolatedLauncherOptionsBuilder().buildRunnerOptions(request);
        if (request.validateRuntimeOwnership()) {
            // arm the runtime-level ownership diagnostics for this (daemon) JVM, including the
            // single-root container-model enforcement: after a root main container is
            // installed in a runtime, code must execute in NESTED containers under it -
            // installing a sibling root fails loudly instead of sharing a plane whose
            // parent-flow would serve a dead sibling's state to the next run
            System.setProperty(OwnershipDiagnostics.VALIDATE_PROPERTY, "true");
        }
        final var runner = new Runner(options, console, err);
        final int result = runner.run();
        if (result == 0 && request.validateRuntimeOwnership()) {
            validateRuntimeOwnership(request.moduleName(), runner.diagnosticContainer(), logger);
        }
        return result;
    }

    /**
     * Whether this run goes through {@link XtcEngine} rather than the CLI {@link Runner}.
     *
     * The engine path runs the module as a nested container under a native plane that is shared by
     * every run in this daemon, which is the shape a resident host wants. It does NOT yet reproduce
     * the CLI's method lookup and argument validation, so a run that passes module arguments still
     * goes through {@link Runner}; those runs are the reason the launcher path stays.
     */
    private static boolean useEngine(final DirectRunRequest request) {
        if (!Boolean.parseBoolean(System.getProperty(ENGINE_PROPERTY, "true"))) {
            return false;
        }
        return request.moduleArgs().isEmpty();
    }

    private static int executeRunWithEngine(final DirectRunRequest request, final Logger logger) {
        if (request.validateRuntimeOwnership()) {
            System.setProperty(OwnershipDiagnostics.VALIDATE_PROPERTY, "true");
        }

        final var engine = engine(request.modulePath(), logger);
        final var method = request.methodName() == null || request.methodName().isBlank()
            ? DEFAULT_METHOD_NAME
            : request.methodName();

        logger.info("[plugin] [DIRECT] Running {}.{}() on the shared engine plane",
            request.moduleName(), method);
        try {
            engine.run(request.moduleName(), method).join();
        } catch (final CompletionException e) {
            final Throwable cause = e.getCause() == null ? e : e.getCause();
            logger.error("Module {} failed: {}", request.moduleName(), cause.toString());
            return 1;
        }

        if (request.validateRuntimeOwnership()) {
            validateRuntimeOwnership(request.moduleName(), engine.diagnosticContainer(), logger);
        }
        return 0;
    }

    /**
     * Shut down every engine this classloader booted.
     *
     * Called by the build service immediately before it closes the classloader. Closing a
     * classloader does not stop threads, and an engine owns a started {@code Runtime} with a live
     * pool - so without this the daemon would accumulate one runtime per build, each pinning a
     * classloader that is supposed to be gone. This is the reason the engines are reachable from a
     * static at all: something has to be able to find them at teardown.
     */
    public static void closeEngines() {
        synchronized (engines) {
            for (final var engine : engines.values()) {
                engine.close();
            }
            engines.clear();
        }
    }

    private static XtcEngine engine(final List<File> modulePath, final Logger logger) {
        final var key = List.copyOf(modulePath);
        synchronized (engines) {
            final var existing = engines.get(key);
            if (existing != null) {
                return existing;
            }
            logger.info("[plugin] [DIRECT] Booting engine plane for module path with {} entries",
                key.size());
            final var engine = XtcEngine.builder().modulePath(key.toArray(File[]::new)).build();
            engines.put(key, engine);
            return engine;
        }
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

    private static void validateRuntimeOwnership(final String moduleName, final Container container, final Logger logger) {
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
