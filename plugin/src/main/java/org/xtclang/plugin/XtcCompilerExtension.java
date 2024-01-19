package org.xtclang.plugin;

import org.gradle.api.provider.Property;

public interface XtcCompilerExtension extends XtcLauncherTaskExtension {
    Property<Boolean> getDisableWarnings();

    Property<Boolean> getStrict();

    Property<Boolean> getQualifiedOutputName();

    Property<Boolean> getVersionedOutputName();

    Property<String> getXtcVersion();

    Property<Boolean> getForceRebuild();
}
