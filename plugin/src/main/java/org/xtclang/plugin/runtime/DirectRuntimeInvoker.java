package org.xtclang.plugin.runtime;

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;

import org.gradle.api.logging.Logger;

import org.xtclang.plugin.XtcLauncherRuntime;

import static org.xtclang.plugin.XtcPluginUtils.failure;

/**
 * Reflective entrypoint from the outer plugin layer into isolated direct-mode
 * runtime execution code.
 */
public final class DirectRuntimeInvoker {
    private static final String EXECUTOR_CLASS = "org.xtclang.plugin.runtime.impl.IsolatedDirectExecutor";

    private DirectRuntimeInvoker() {
    }

    public static int executeCompile(final XtcLauncherRuntime runtime, final DirectCompileRequest request, final Logger logger) {
        return invoke(runtime, "executeCompile", new Class<?>[]{DirectCompileRequest.class, Logger.class}, request, logger);
    }

    public static int executeRun(final XtcLauncherRuntime runtime, final DirectRunRequest request, final Logger logger) {
        return invoke(runtime, "executeRun", new Class<?>[]{DirectRunRequest.class, Logger.class}, request, logger);
    }

    public static int executeTest(final XtcLauncherRuntime runtime, final DirectTestRequest request, final Logger logger) {
        return invoke(runtime, "executeTest", new Class<?>[]{DirectTestRequest.class, Logger.class}, request, logger);
    }

    private static int invoke(
            final XtcLauncherRuntime runtime,
            final String methodName,
            final Class<?>[] parameterTypes,
            final Object request,
            final Logger logger) {

        final URL[] runtimeUrls = createRuntimeUrls(runtime);
        logger.info("[plugin] [DIRECT] Creating isolated runtime '{}' with {} entries", runtime.source(), runtimeUrls.length);

        try (var loader = new PluginRuntimeClassLoader(runtimeUrls, DirectRuntimeInvoker.class.getClassLoader())) {
            final var executorType = loader.loadClass(EXECUTOR_CLASS);
            final var method = executorType.getMethod(methodName, parameterTypes);
            return (Integer) method.invoke(null, request, logger);
        } catch (final InvocationTargetException e) {
            final var cause = e.getCause();
            throw failure(cause == null ? e : cause, "Direct runtime invocation failed in {}()", methodName);
        } catch (final Exception e) {
            throw failure(e, "Failed to invoke isolated direct runtime method {}()", methodName);
        }
    }

    private static URL[] createRuntimeUrls(final XtcLauncherRuntime runtime) {
        final URL[] urls = new URL[runtime.classpath().size() + 1];
        urls[0] = codeSourceUrl();
        for (int i = 0; i < runtime.classpath().size(); i++) {
            urls[i + 1] = toUrl(runtime.classpath().get(i));
        }
        return urls;
    }

    private static URL codeSourceUrl() {
        final var codeSource = DirectRuntimeInvoker.class.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            throw failure("Plugin code source is not available for isolated direct runtime loading");
        }
        return codeSource.getLocation();
    }

    private static URL toUrl(final java.io.File file) {
        try {
            return file.toURI().toURL();
        } catch (final MalformedURLException e) {
            throw failure(e, "Invalid runtime classpath entry: {}", file.getAbsolutePath());
        }
    }
}
