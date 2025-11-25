package org.xtclang.plugin.tasks;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;

/**
 * Minimal base class for XTC tasks using modern Gradle best practices:
 * <ul>
 *   <li>No field storage - services accessed via @Inject getters on-demand</li>
 *   <li>Configuration cache compatible - no Project references</li>
 *   <li>Lazy property evaluation - configured by plugin, evaluated at execution</li>
 *   <li>Minimal API surface - only utility methods</li>
 * </ul>
 *
 * <p>This base class provides only common utility methods. Most tasks should
 * extend DefaultTask directly unless they need these specific utilities.
 */
public abstract class XtcDefaultTask extends DefaultTask {

    /**
     * Gradle automatically injects services via these abstract getters.
     * Service references are configuration cache compatible.
     * Subclasses should call these getters directly instead of storing in fields.
     */
    @Inject
    public abstract ObjectFactory getObjects();

    @Inject
    public abstract ProviderFactory getProviders();

    /**
     * Configure this task to never be UP-TO-DATE and never be cached.
     * Useful for tasks with side effects like running executables.
     * <p>
     * This matches the behavior of Gradle's JavaExec task.
     * Configuration cache safe - must be called during task construction.
     */
    protected final void considerNeverUpToDate() {
        getOutputs().upToDateWhen(_ -> false);
        getOutputs().cacheIf(_ -> false);
        getLogger().info("[plugin] Task '{}' configured to always run (never UP-TO-DATE, never cached)", getName());
    }
}
