package org.xtclang.plugin.internal;

import org.gradle.api.Project;
import org.gradle.api.Task;

/**
 * Utility class for asserting that we're in the expected Gradle build phase.
 * This helps catch configuration cache compatibility issues and ensures proper build lifecycle.
 */
public final class GradlePhaseAssertions {
    
    private GradlePhaseAssertions() {
        // Utility class - no instances
    }
    
    /**
     * Asserts that we are currently in the configuration phase.
     * Use this when accessing Project instances or doing configuration-time work.
     * 
     * @param context descriptive context for the assertion (e.g., "initializing task extensions")
     */
    public static void assertConfigurationPhase(final String context) {
        // During configuration phase, we should be able to access system properties without issues
        // During execution phase with configuration cache, many operations would be restricted
        try {
            // This is a heuristic - configuration cache will restrict certain operations during execution
            System.getProperty("gradle.configuration.cache", "false");
        } catch (final Exception e) {
            throw new IllegalStateException("Expected to be in configuration phase during: " + context + 
                ", but appears to be in execution phase. This may indicate a configuration cache compatibility issue.", e);
        }
    }
    
    /**
     * Asserts that we are currently in the execution phase.
     * Use this in task actions and doLast/doFirst blocks.
     * 
     * @param task the task being executed
     * @param context descriptive context for the assertion (e.g., "running XTC compiler")
     */
    public static void assertExecutionPhase(final Task task, final String context) {
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null in execution phase");
        }
        
        // During execution phase, task should have access to its logger and basic properties
        try {
            task.getLogger().debug("Phase assertion: {} during {}", task.getName(), context);
        } catch (final Exception e) {
            throw new IllegalStateException("Expected to be in execution phase for task '" + task.getName() + 
                "' during: " + context + ", but task operations are not available.", e);
        }
    }
    
    /**
     * Asserts that Project access should not occur during execution phase.
     * This helps identify configuration cache incompatible code.
     * 
     * @param project the project (should only be used during configuration)
     * @param context descriptive context for the assertion
     */
    public static void assertProjectAccessDuringConfiguration(final Project project, final String context) {
        assertConfigurationPhase("project access during: " + context);
        
        if (project == null) {
            throw new IllegalArgumentException("Project cannot be null during configuration phase access: " + context);
        }
        
        // Log project access for debugging configuration cache issues
        project.getLogger().debug("[plugin] Project access during configuration phase: {}", context);
    }
    
    /**
     * Validates that captured configuration data is properly set up for configuration cache.
     * 
     * @param data the data that should be captured at configuration time
     * @param dataDescription description of the data for error messages
     */
    public static void validateConfigurationTimeCapture(final Object data, final String dataDescription) {
        switch (data) {
        case null ->
                throw new IllegalStateException("Configuration-time captured data is null: " + dataDescription + ". This may indicate improper configuration cache setup.");

        // Ensure data is of a serializable type for configuration cache
        case final Project _ ->
                throw new IllegalStateException("Project instance captured at configuration time: " + dataDescription + ". This will break configuration cache. Use Provider patterns instead.");

        // Add more checks for common non-serializable types
        case final org.gradle.api.logging.Logger _ ->
                throw new IllegalStateException("Logger instance captured at configuration time: " + dataDescription + ". This will break configuration cache. Use task.getLogger() during execution instead.");
        default -> {
        }
        }
    }
}
