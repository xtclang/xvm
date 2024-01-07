package org.xtclang.plugin.internal;

import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.xtclang.plugin.XtcCompilerExtension;

import javax.inject.Inject;

public class DefaultXtcCompilerExtension extends DefaultXtcLauncherTaskExtension implements XtcCompilerExtension {
    private final Property<Boolean> disableWarnings;
    private final Property<Boolean> isStrict;
    private final Property<Boolean> hasQualifiedOutputName;
    private final Property<Boolean> hasVersionedOutputName;
    private final Property<Boolean> shouldForceRebuild;
    private final DirectoryProperty additionalOutputDir;

    @Inject
    public DefaultXtcCompilerExtension(final Project project) {
        super(project);
        this.disableWarnings = objects.property(Boolean.class).value(false);
        this.isStrict = objects.property(Boolean.class).value(false);
        this.hasQualifiedOutputName = objects.property(Boolean.class).value(false);
        this.hasVersionedOutputName = objects.property(Boolean.class).value(false);
        this.shouldForceRebuild = objects.property(Boolean.class).value(false);
        this.additionalOutputDir = objects.directoryProperty();
    }

    @Override
    public DirectoryProperty getAdditionalOutputDir() {
        return additionalOutputDir;
    }

    @Override
    public Property<Boolean> getDisableWarnings() {
        return disableWarnings;
    }

    @Override
    public Property<Boolean> getStrict() {
        return isStrict;
    }

    @Override
    public Property<Boolean> getQualifiedOutputName() {
        return hasQualifiedOutputName;
    }

    @Override
    public Property<Boolean> getVersionedOutputName() {
        return hasVersionedOutputName;
    }

    @Override
    public Property<Boolean> getForceRebuild() {
        return shouldForceRebuild;
    }
}
