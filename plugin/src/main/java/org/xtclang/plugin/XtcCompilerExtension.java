package org.xtclang.plugin;

import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

public interface XtcCompilerExtension extends XtcTaskExtension {
    Property<Boolean> getNoWarn();
    Property<Boolean> getStrict();
    Property<Boolean> getQualifiedOutputName();
    Property<Boolean> getVersionedOutputName();
    Property<Boolean> getForceRebuild();
    Property<String> getOutputFilename();
    MapProperty<String, String> getRenameOutput();
}
