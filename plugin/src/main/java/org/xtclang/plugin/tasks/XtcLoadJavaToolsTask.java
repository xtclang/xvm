package org.xtclang.plugin.tasks;

import javax.inject.Inject;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import org.jetbrains.annotations.NotNull;

import static org.xtclang.plugin.XtcJavaToolsRuntime.ensureJavaToolsInClasspath;

/**
 * Task that loads javatools.jar into the plugin classloader.
 * This task must run before any task that uses javatools types (CompilerOptions, RunnerOptions, etc.).
 * Configuration cache compatible - all values captured at configuration time.
 */
public abstract class XtcLoadJavaToolsTask extends DefaultTask {

    @Input
    public abstract Property<@NotNull String> getProjectVersion();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract Property<@NotNull FileCollection> getJavaToolsConfiguration();

    @Internal
    public abstract Property<@NotNull FileTree> getXdkFileTree();

    @SuppressWarnings({"this-escape", "ConstructorNotProtectedInAbstractClass"})
    @Inject
    public XtcLoadJavaToolsTask() {
        // Task is never up-to-date - always loads javatools (TODO: Use the utility method for this as in XtcRunTask?)
        getOutputs().upToDateWhen(_ -> false);
    }

    @TaskAction
    public void loadJavaTools() {
        final var logger = getLogger();
        logger.info("[plugin] Loading javatools.jar into plugin classloader");
        // Log classpath before loading javatools
        final var classLoader = getClass().getClassLoader();
        //lines.add("[plugin] Classpath BEFORE ensureJavaToolsInClasspath:");
        //lines.addAll(logClasspath(classLoader));
        // Use providers that were set at configuration time
        final var versionProvider = getProjectVersion().map(v -> v);
        final var javaToolsProvider = getJavaToolsConfiguration().map(fc -> fc);
        final var xdkProvider = getXdkFileTree().map(ft -> ft);
        final boolean changed = ensureJavaToolsInClasspath(versionProvider, javaToolsProvider, xdkProvider, logger);
        // Log classpath after loading javatools
        if (changed) {
            final var lines = new ArrayList<>(logClasspath(classLoader));
            lines.forEach(logger::info);
        }
    }

    private static List<String> logClasspath(final ClassLoader classLoader) {
        final var lines = new ArrayList<String>();
        if (classLoader instanceof final URLClassLoader urlClassLoader) {
            final var urls = urlClassLoader.getURLs();
            lines.add("[plugin]   URLClassLoader with " + urls.length + " URLs:");
            for (int i = 0; i < urls.length; i++) {
                lines.add("[plugin]     [" + i + "] " + urls[i]);
            }
            return lines;
        }
        lines.add("[plugin]   ClassLoader type: " + classLoader.getClass().getName());
        lines.add("[plugin]   (Not a URLClassLoader, cannot enumerate classpath)");
        //lines.forEach(logger::info);
        return lines;
    }
}
