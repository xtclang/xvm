package org.xtclang.plugin.runtime;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gradle.api.logging.Logger;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import org.xtclang.plugin.XtcLauncherRuntime;

/**
 * Build-scoped owner for DIRECT execution. Requests reuse a compatible isolated embedding session
 * during this build; close releases every session. Persistent execution uses a separate owner.
 */
public abstract class DirectRuntimeBuildService
        implements BuildService<BuildServiceParameters.None>, AutoCloseable {
    private final Map<DirectRuntimeFingerprint, IsolatedRuntime> runtimes = new HashMap<>();

    public int executeCompile(final XtcLauncherRuntime runtime, final List<File> modules, final DirectCompileRequest request, final Logger logger) {
        return invoke(runtime, modules, request, logger);
    }

    public int executeRun(final XtcLauncherRuntime runtime, final List<File> modules, final DirectRunRequest request, final Logger logger) {
        return invoke(runtime, modules, request, logger);
    }

    public int executeTest(final XtcLauncherRuntime runtime, final List<File> modules, final DirectTestRequest request, final Logger logger) {
        return invoke(runtime, modules, request, logger);
    }

    private synchronized int invoke(final XtcLauncherRuntime runtime, final List<File> modules, final Object request, final Logger logger) {
        final var key = DirectRuntimeFingerprint.from(runtime, IsolatedRuntime.codeSource(), modules);
        final var executor = runtimes.computeIfAbsent(key, ignored -> {
            logger.info("[plugin] [DIRECT] Creating build-scoped isolated runtime '{}'", runtime.source());
            return new IsolatedRuntime(runtime.classpath(), modules);
        });
        return executor.execute(request, new RuntimeOutput() {
            @Override
            public void out(final String text) { logger.lifecycle(text); }
            @Override
            public void err(final String text) { logger.error(text); }
        });
    }

    @Override
    public synchronized void close() {
        RuntimeException failure = null;
        for (final var runtime : runtimes.values()) {
            try {
                runtime.close();
            } catch (final RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        runtimes.clear();
        if (failure != null) {
            throw failure;
        }
    }
}
