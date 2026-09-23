package org.xtclang.plugin.runtime;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.List;

/**
 * Owns an implementation loader and embedding session, independently of the host's lifetime.
 * A build service or persistent worker owns this object and must serialize calls to execute.
 */
public final class IsolatedRuntime implements RuntimeExecutor {
    private final PluginRuntimeClassLoader loader;
    private final RuntimeExecutor executor;

    public IsolatedRuntime(final List<File> classpath, final List<File> coreModules) {
        try {
            final var urls = new URL[classpath.size() + 1];
            urls[0] = codeSource();
            for (int i = 0; i < classpath.size(); i++) {
                urls[i + 1] = classpath.get(i).toURI().toURL();
            }
            loader = new PluginRuntimeClassLoader(urls, IsolatedRuntime.class.getClassLoader());
            loader.setDefaultAssertionStatus(true);
            try {
                executor = (RuntimeExecutor) loader.loadClass("org.xtclang.plugin.runtime.impl.IsolatedDirectExecutor")
                    .getConstructor(List.class).newInstance(coreModules);
            } catch (final ReflectiveOperationException | RuntimeException | Error failure) {
                try {
                    loader.close();
                } catch (final IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        } catch (final ReflectiveOperationException | IOException e) {
            throw new IllegalStateException("Unable to create isolated embedding session", e);
        }
    }

    public static URL codeSource() {
        final var source = IsolatedRuntime.class.getProtectionDomain().getCodeSource();
        if (source == null) {
            throw new IllegalStateException("Plugin code source is unavailable");
        }
        return source.getLocation();
    }

    @Override
    public int execute(final Object request, final RuntimeOutput output) {
        final var thread = Thread.currentThread();
        final var previous = thread.getContextClassLoader();
        thread.setContextClassLoader(loader);
        try {
            return executor.execute(request, output);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    @Override
    public boolean cancel(final Duration timeout) {
        return executor.cancel(timeout);
    }

    @Override
    public void close(final Duration timeout) {
        // Do not unload implementation classes if native shutdown has failed.
        executor.close(timeout);
        try {
            loader.close();
        } catch (final IOException e) {
            throw new IllegalStateException("Unable to close runtime loader", e);
        }
    }
}
