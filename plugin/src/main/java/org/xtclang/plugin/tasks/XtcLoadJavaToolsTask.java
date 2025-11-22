package org.xtclang.plugin.tasks;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcJavaToolsRuntime;

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

    @SuppressWarnings("this-escape")
    @Inject
    public XtcLoadJavaToolsTask() {
        // Task is never up-to-date - always loads javatools
        getOutputs().upToDateWhen(task -> false);
    }

    @TaskAction
    public void loadJavaTools() {
        getLogger().info("[plugin] Loading javatools.jar into plugin classloader");

        // Use providers that were set at configuration time
        final Provider<@NotNull String> versionProvider = getProjectVersion().map(v -> v);
        final Provider<@NotNull FileCollection> javaToolsProvider = getJavaToolsConfiguration().map(fc -> fc);
        final Provider<@NotNull FileTree> xdkProvider = getXdkFileTree().map(ft -> ft);

        XtcJavaToolsRuntime.ensureJavaToolsInClasspath(
                versionProvider,
                javaToolsProvider,
                xdkProvider,
                getLogger()
        );

        getLogger().info("[plugin] Java tools loaded successfully");
    }
}