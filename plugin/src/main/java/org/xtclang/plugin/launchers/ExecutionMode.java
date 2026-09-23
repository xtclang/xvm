package org.xtclang.plugin.launchers;

/**
 * Execution mode for XTC launcher tasks.
 */
public enum ExecutionMode {
    /**
     * Direct in-process execution using the embedding API.
     * Fastest, but shares JVM with Gradle.
     */
    DIRECT,

    /**
     * Experimental execution in a checkout-local worker reused across builds. Opt-in only;
     * ordinary DIRECT execution retains its build-scoped lifetime.
     */
    PERSISTENT,

    /**
     * Forked JVM with inherited I/O (stdout/stderr go to parent).
     * Default mode - isolated process with visible output.
     */
    ATTACHED,

    /**
     * Forked JVM running in background with file redirects.
     * Process continues after Gradle exits.
     */
    DETACHED
}
