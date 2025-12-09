package org.xtclang.plugin.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;

/**
 * Minimal base class for XTC tasks using modern Gradle best practices:
 * <ul>
 *   <li>Constructor injection for services - avoids this-escape issues</li>
 *   <li>Configuration cache compatible - no Project references at execution time</li>
 *   <li>Lazy property evaluation - configured by plugin, evaluated at execution</li>
 *   <li>Minimal API surface - protected fields for subclass access</li>
 * </ul>
 *
 * <p>This base class provides common services as protected fields. Most tasks should
 * extend DefaultTask directly unless they need these specific utilities.
 */
@SuppressWarnings("this-escape") // Safe: getLogger() is from DefaultTask, doesn't depend on subclass state
public abstract class XtcDefaultTask extends DefaultTask {
    /**
     * Gradle ObjectFactory for creating managed objects.
     * Injected via constructor - configuration cache compatible.
     */
    protected final ObjectFactory objects;

    /**
     * Task logger, cached for convenience.
     * Uses the task's own logger from DefaultTask - configuration cache compatible.
     */
    protected final Logger logger;

    /**
     * Constructor with injected ObjectFactory.
     * Subclasses must pass ObjectFactory from their @Inject constructor.
     *
     * @param objects the Gradle ObjectFactory for creating managed objects
     */
    protected XtcDefaultTask(final ObjectFactory objects) {
        this.objects = objects;
        this.logger = getLogger();
    }

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
        logger.info("[plugin] Task '{}' configured to always run (never UP-TO-DATE, never cached)", getName());
    }
}
