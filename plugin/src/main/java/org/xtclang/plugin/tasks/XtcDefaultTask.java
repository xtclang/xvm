package org.xtclang.plugin.tasks;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Internal;

import org.jetbrains.annotations.NotNull;

import static org.xtclang.plugin.XtcPluginConstants.PROPERTY_VERBOSE_LOGGING_OVERRIDE;

/**
 * Base class for XTC tasks providing centralized verbose logging configuration.
 * <ul>
 *   <li>Configuration cache compatible - no field storage, services via @Inject</li>
 *   <li>Lazy property evaluation - configured by plugin, evaluated at execution</li>
 *   <li>Minimal API surface - only verbose logging override property</li>
 * </ul>
 */
public abstract class XtcDefaultTask extends DefaultTask {

    /**
     * Gradle automatically injects services via these abstract getters.
     * Service references are configuration cache compatible.
     */
    @Inject
    public abstract ObjectFactory getObjects();

    @Inject
    public abstract ProviderFactory getProviders();

    /**
     * Verbose logging override as a lazy property instead of eager field.
     * Plugin sets convention from project property at configuration time.
     * Task evaluates lazily at execution time.
     */
    @Internal  // Not a task input - doesn't affect up-to-date checks
    public abstract Property<@NotNull Boolean> getVerboseLoggingOverride();

    /**
     * Check if verbose logging is enabled.
     * Respects Gradle --info/--debug flags AND project property overrides.
     * <p>
     * This method is called at execution time, so it's safe to evaluate properties.
     */
    protected boolean hasVerboseLogging() {
        final var logger = getLogger();
        // Gradle log levels take precedence
        if (logger.isInfoEnabled() || logger.isDebugEnabled()) {
            return true;
        }

        // Check project property override
        return getVerboseLoggingOverride().getOrElse(false);
    }

    /**
     * Called at the start of task execution.
     * Subclasses should override and call super.executeTask() to add phase assertions.
     */
    protected void executeTask() {
        // Base implementation does nothing - subclasses add phase assertions
    }

    /**
     * Initialize default property values.
     * Plugin can override these conventions.
     */
    @SuppressWarnings("this-escape") // Calling @Inject methods in constructor is safe
    public XtcDefaultTask() {
        // Read verbose logging override from project properties
        final var verboseOverride = getProviders()
            .gradleProperty(PROPERTY_VERBOSE_LOGGING_OVERRIDE)
            .map(Boolean::parseBoolean)
            .orElse(false);

        getVerboseLoggingOverride().convention(verboseOverride);
    }
}
