package org.xvm.plugin;

import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

public interface XtcCompilerExtension extends XtcTaskExtension {
    // compiler only
    Property<Boolean> getNoWarn();

    // compiler and runtime
    Property<Boolean> getVerbose();

    // compiler only
    Property<Boolean> getStrict();

    Property<Boolean> getQualifiedOutputName();

    // compiler only.
    Property<Boolean> getVersionedOutputName();

    // compiler only
    Property<Boolean> getForceRebuild(); // TODO: Tie this in with a dependency on the "clean" lifecycle task

    // output filename, just the filename
    Property<String> getOutputFilename();

    // compiler only
    MapProperty<String, String> getRenameOutput();
}
