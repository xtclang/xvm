package org.xtclang.plugin;

import org.gradle.api.provider.Property;

import org.jetbrains.annotations.NotNull;

/**
 * Global XTC plugin extension for project-wide configuration.
 * Access via: project.extensions.getByType(XtcExtension.class)
 */
@FunctionalInterface
public interface XtcExtension {
    /**
     * Enable verbose logging for all XTC tasks in this project.
     * This supplements Gradle's --info and --debug flags.
     * <p>
     * Can be set via project property: -Pxtc.verbose=true
     * Or in build script: xtc { verboseLogging = true }
     */
    Property<@NotNull Boolean> getVerboseLogging();
}
