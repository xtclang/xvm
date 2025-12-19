package org.xtclang.plugin.launchers;

/**
 * Execution mode for XTC launcher tasks.
 */
public enum ExecutionMode {
    /**
     * Direct in-process execution using ServiceLoader.
     * Fastest, but shares JVM with Gradle.
     */
    DIRECT,

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