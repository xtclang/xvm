package org.xtclang.plugin;

import org.gradle.api.provider.Property;

import org.jetbrains.annotations.NotNull;

public interface XtcCompilerExtension extends XtcLauncherTaskExtension {
    Property<@NotNull Boolean> getDisableWarnings();

    Property<@NotNull Boolean> getStrict();

    Property<@NotNull Boolean> getQualifiedOutputName();

    Property<@NotNull Boolean> getVersionedOutputName();

    Property<@NotNull String> getXtcVersion();

    Property<@NotNull Boolean> getRebuild();
}
