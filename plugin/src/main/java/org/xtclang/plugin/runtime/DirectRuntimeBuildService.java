package org.xtclang.plugin.runtime;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import org.xtclang.plugin.XtcLauncherRuntime;

import static org.xtclang.plugin.XtcPluginUtils.failure;

/**
 * Build-scoped owner for isolated direct-mode runtimes.
 *
 * <p>The important constraint here is scope: direct execution may reuse an isolated
 * runtime within one build, but it must not leak that runtime into later builds via
 * daemon-global static state. Gradle shared services give us exactly that lifecycle.
 *
 * <p>Each cached entry is keyed by the resolved launcher runtime classpath plus the
 * plugin code source. That lets multiple direct tasks in the same build reuse the
 * same isolated classloader when they truly target the same runtime, while still
 * separating builds or runtime changes cleanly.
 */
public abstract class DirectRuntimeBuildService
        implements BuildService<BuildServiceParameters.None>, AutoCloseable {

    private static final String EXECUTOR_CLASS = "org.xtclang.plugin.runtime.impl.IsolatedDirectExecutor";
    private static final Logger LOG = Logging.getLogger(DirectRuntimeBuildService.class);

    private final Map<DirectRuntimeFingerprint, RuntimeEntry> runtimes = new ConcurrentHashMap<>();

    public int executeCompile(final XtcLauncherRuntime runtime, final DirectCompileRequest request, final Logger logger) {
        return invoke(runtime, "executeCompile", new Class<?>[]{DirectCompileRequest.class, Logger.class}, request, logger);
    }

    public int executeRun(final XtcLauncherRuntime runtime, final DirectRunRequest request, final Logger logger) {
        return invoke(runtime, "executeRun", new Class<?>[]{DirectRunRequest.class, Logger.class}, request, logger);
    }

    public int executeTest(final XtcLauncherRuntime runtime, final DirectTestRequest request, final Logger logger) {
        return invoke(runtime, "executeTest", new Class<?>[]{DirectTestRequest.class, Logger.class}, request, logger);
    }

    private int invoke(
            final XtcLauncherRuntime runtime,
            final String methodName,
            final Class<?>[] parameterTypes,
            final Object request,
            final Logger logger) {

        final var entry = getOrCreateRuntime(runtime, logger);
        try {
            return (Integer) entry.executorMethod(methodName, parameterTypes).invoke(null, request, logger);
        } catch (final InvocationTargetException e) {
            final var cause = e.getCause();
            throw failure(cause == null ? e : cause, "Direct runtime invocation failed in {}()", methodName);
        } catch (final Exception e) {
            throw failure(e, "Failed to invoke isolated direct runtime method {}()", methodName);
        }
    }

    private RuntimeEntry getOrCreateRuntime(final XtcLauncherRuntime runtime, final Logger logger) {
        final var fingerprint = DirectRuntimeFingerprint.from(runtime, codeSourceUrl());
        final var existing = runtimes.get(fingerprint);
        if (existing != null) {
            logger.info("[plugin] [DIRECT] Reusing isolated runtime '{}' with {} entries (cache hit, cached={})",
                runtime.source(), runtime.classpath().size(), runtimes.size());
            logger.debug("[plugin] [DIRECT] Reused runtime fingerprint: {}", fingerprint.describeForLogging());
            return existing;
        }

        logger.info("[plugin] [DIRECT] Cache miss for isolated runtime '{}' (cached={})",
            runtime.source(), runtimes.size());
        logger.debug("[plugin] [DIRECT] Requested runtime fingerprint: {}", fingerprint.describeForLogging());
        return runtimes.computeIfAbsent(fingerprint, ignored -> createRuntimeEntry(runtime, logger));
    }

    private RuntimeEntry createRuntimeEntry(final XtcLauncherRuntime runtime, final Logger logger) {
        final URL[] runtimeUrls = createRuntimeUrls(runtime);
        logger.info("[plugin] [DIRECT] Creating build-scoped isolated runtime '{}' with {} entries",
            runtime.source(), runtimeUrls.length);
        logger.debug("[plugin] [DIRECT] Runtime classpath:\n{}",
            runtime.classpath().stream()
                .map(file -> "[plugin] [DIRECT]   " + file.getAbsolutePath())
                .collect(Collectors.joining("\n")));

        try {
            final var loader = new PluginRuntimeClassLoader(runtimeUrls, getClass().getClassLoader());
            final var executorType = loader.loadClass(EXECUTOR_CLASS);
            final var entry = new RuntimeEntry(
                loader,
                executorType.getMethod("executeCompile", DirectCompileRequest.class, Logger.class),
                executorType.getMethod("executeRun", DirectRunRequest.class, Logger.class),
                executorType.getMethod("executeTest", DirectTestRequest.class, Logger.class)
            );
            logger.debug("[plugin] [DIRECT] Cached isolated runtimes after creation: {}", runtimes.size() + 1);
            return entry;
        } catch (final Exception e) {
            throw failure(e, "Failed to create isolated direct runtime for '{}'", runtime.source());
        }
    }

    private static URL[] createRuntimeUrls(final XtcLauncherRuntime runtime) {
        // The plugin code source comes first so the isolated loader can see the plugin-side
        // runtime bridge classes. The resolved XDK runtime entries follow after that.
        final URL[] urls = new URL[runtime.classpath().size() + 1];
        urls[0] = codeSourceUrl();
        for (int i = 0; i < runtime.classpath().size(); i++) {
            urls[i + 1] = toUrl(runtime.classpath().get(i));
        }
        return urls;
    }

    private static URL codeSourceUrl() {
        final var codeSource = DirectRuntimeBuildService.class.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            throw failure("Plugin code source is not available for isolated direct runtime loading");
        }
        return codeSource.getLocation();
    }

    private static URL toUrl(final File file) {
        try {
            return file.toURI().toURL();
        } catch (final MalformedURLException e) {
            throw failure(e, "Invalid runtime classpath entry: {}", file.getAbsolutePath());
        }
    }

    @Override
    public void close() {
        LOG.info("[plugin] [DIRECT] Closing build-scoped runtime service with {} cached runtime(s)", runtimes.size());
        if (LOG.isDebugEnabled()) {
            runtimes.keySet().forEach(fingerprint ->
                LOG.debug("[plugin] [DIRECT] Releasing runtime fingerprint: {}", fingerprint.describeForLogging()));
        }
        runtimes.values().forEach(RuntimeEntry::close);
        runtimes.clear();
    }

    private record RuntimeEntry(
            PluginRuntimeClassLoader classLoader,
            Method compileMethod,
            Method runMethod,
            Method testMethod) implements Closeable {

        Method executorMethod(final String methodName, final Class<?>[] parameterTypes) throws NoSuchMethodException {
            return switch (methodName) {
                case "executeCompile" -> compileMethod;
                case "executeRun" -> runMethod;
                case "executeTest" -> testMethod;
                default -> throw new NoSuchMethodException(methodName + Arrays.toString(parameterTypes));
            };
        }

        @Override
        public void close() {
            try {
                classLoader.close();
            } catch (final IOException e) {
                throw failure(e, "Failed to close isolated direct runtime classloader");
            }
        }
    }
}
