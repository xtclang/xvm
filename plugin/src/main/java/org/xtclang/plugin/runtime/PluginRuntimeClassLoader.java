package org.xtclang.plugin.runtime;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/**
 * Child-first loader for runtime implementation classes that must resolve against
 * the selected XDK runtime rather than the already-loaded plugin classloader.
 */
public final class PluginRuntimeClassLoader extends URLClassLoader {
    private static final List<String> CHILD_FIRST_PREFIXES = List.of(
        "org.xtclang.plugin.runtime.impl."
    );

    public PluginRuntimeClassLoader(final URL[] urls, final ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            final var loaded = findLoadedClass(name);
            if (loaded != null) {
                return resolveIfNeeded(loaded, resolve);
            }

            if (isChildFirst(name)) {
                try {
                    final var found = findClass(name);
                    return resolveIfNeeded(found, resolve);
                } catch (final ClassNotFoundException ignored) {
                    // Fall through to parent delegation. Higher-level runtime owners should
                    // log this using the Gradle logger if this becomes diagnostically useful.
                }
            }

            return super.loadClass(name, resolve);
        }
    }

    private static boolean isChildFirst(final String name) {
        return CHILD_FIRST_PREFIXES.stream().anyMatch(name::startsWith);
    }

    private Class<?> resolveIfNeeded(final Class<?> type, final boolean resolve) {
        if (resolve) {
            resolveClass(type);
        }
        return type;
    }
}
