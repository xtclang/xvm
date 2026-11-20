package org.xtclang.plugin;

import org.gradle.api.provider.Property;

import org.jetbrains.annotations.NotNull;

public interface XtcCompilerExtension extends XtcLauncherTaskExtension {
    Property<@NotNull Boolean> getDisableWarnings();

    Property<@NotNull Boolean> getStrict();

    Property<@NotNull Boolean> getQualifiedOutputName();

    Property<@NotNull Boolean> getVersionedOutputName();

    Property<@NotNull String> getXtcVersion();

    /**
     * The time to stamp compiled modules with, passed to the compiler as {@code --timestamp}: "now", or an
     * ISO-8601 instant such as 2026-01-01T00:00:00Z. Unset, the modules carry no time, so that the same source
     * compiles to the same bytes and builds that depend on them stay up to date.
     */
    Property<@NotNull String> getTimestamp();

    Property<@NotNull Boolean> getRebuild();
}
