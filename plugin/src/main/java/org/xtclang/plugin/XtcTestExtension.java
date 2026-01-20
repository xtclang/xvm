package org.xtclang.plugin;

import org.gradle.api.provider.Property;
import org.jetbrains.annotations.NotNull;

/**
 * This is the "xtcTest" extension. It can be used to set up common test
 * behavior on a per-project level, similar to the "application" configuration
 * in the Gradle built-in plugins.
 * <p>
 * As with every other extension, by convention, it should be possible to change, extend
 * or override parts of it on individual test task level.
 * <p>
 * Test filtering (include/exclude) is handled at the source set level, not here.
 */
public interface XtcTestExtension extends XtcRuntimeExtension {

    /**
     * Whether to fail the build if any tests fail.
     * Defaults to true.
     *
     * @return property controlling build failure on test failure
     */
    Property<@NotNull Boolean> getFailOnTestFailure();
}
