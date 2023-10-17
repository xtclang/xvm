package org.xvm.plugin;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

final class OnDemandLibraryLoader {

    private final ProjectDelegate project;

    OnDemandLibraryLoader(final XtcProjectDelegate project) {
        this.project = project;
    }

    void addToClasspath(final File file) {
        addToClasspath(project, file);
    }

    /**
     * Hacks the system classloader to add a classpath entry at runtime.<br /><br />
     *
     * <b>Example</b><br /><br />
     * {@code JavaToolsLoader.addToClasspath(new File('example.jar'));}<br />
     * {@code ClassInExampleJar.doStuff();}
     *
     * @param file The jar file to add to the classpath
     */
    static void addToClasspath(final ProjectDelegate project, final File file) {
        try {
            final URL url = file.toURI().toURL();
            final URLClassLoader classLoader = (URLClassLoader)ClassLoader.getSystemClassLoader();
            final Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            if (!method.trySetAccessible()) {
                throw new SecurityException("No permissions to set accessibility of method: " + method.getName());
            }
            method.invoke(classLoader, url);
            project.lifecycle("{} Added '{}' to classpath", project.prefix(), file);
        } catch (final Exception e) {
            throw project.buildException("Unexpected exception adding {} to runtime classpath.", e);
        }
    }
}
