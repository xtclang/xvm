package org.xtclang.plugin.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import org.jetbrains.annotations.NotNull;

/**
 * Task that displays and provides access to XDK/XTC version information.
 * This task is cacheable and configuration-cache compatible.
 */
@CacheableTask
public abstract class XtcVersionTask extends DefaultTask {

    /**
     * The XDK version (e.g., "0.4.4-SNAPSHOT").
     * This is an input property that makes the task participate in up-to-date checking.
     *
     * @return the XDK version property
     */
    @Input
    public abstract Property<@NotNull String> getXdkVersion();

    /**
     * The semantic version string (e.g., "org.xtclang:project-name:0.4.4-SNAPSHOT").
     * This is an input property that makes the task participate in up-to-date checking.
     *
     * @return the semantic version property
     */
    @Input
    public abstract Property<@NotNull String> getSemanticVersion();

    /**
     * Whether verbose logging is enabled (info/debug log level or verbose logging override property).
     * This is an internal property that affects how version information is logged.
     * Not marked as @Input because it only affects logging behavior, not task outputs.
     *
     * @return the verbose logging property
     */
    @Internal
    public abstract Property<@NotNull Boolean> getVerboseLogging();

    /**
     * Convenience getter for the XDK version value.
     * Use this in build scripts to access the version programmatically.
     *
     * @return the XDK version string
     */
    @SuppressWarnings("unused")
    @Internal
    public String getXdkVersionValue() {
        return getXdkVersion().get();
    }

    /**
     * Convenience getter for the semantic version value.
     * Use this in build scripts to access the semantic version programmatically.
     *
     * @return the semantic version string
     */
    @SuppressWarnings("unused")
    @Internal
    public String getSemanticVersionValue() {
        return getSemanticVersion().get();
    }

    @TaskAction
    public void displayVersion() {
        final var logger = getLogger();
        //final var xdkVer = getXdkVersion().get();
        final var semanticVer = getSemanticVersion().get();

        if (getVerboseLogging().get()) {
            //logger.lifecycle("[plugin] XDK/XTC Version: {}", xdkVer);
            logger.lifecycle("[plugin] Project Semantic Version: {}", semanticVer);
        }
    }
}
