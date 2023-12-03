package org.xtclang.plugin.internal;

import org.gradle.api.Project;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.xtclang.plugin.XtcCompilerExtension;

import javax.inject.Inject;
import java.util.HashMap;

public class DefaultXtcCompilerExtension extends DefaultXtcTaskExtension implements XtcCompilerExtension {
    private final Property<Boolean> disableWarnings;
    private final Property<Boolean> isStrict;
    private final Property<Boolean> hasQualifiedOutputName;
    private final Property<Boolean> hasVersionedOutputName;
    private final Property<Boolean> shouldForceRebuild;
    private final MapProperty<String, String> renameOutput;
    private final Property<String> outputFilename;

    @Inject
    public DefaultXtcCompilerExtension(final Project project) {
        super(project);
        this.disableWarnings = objects.property(Boolean.class).value(false);
        this.isStrict = objects.property(Boolean.class).value(false);
        this.hasQualifiedOutputName = objects.property(Boolean.class).value(false);
        this.hasVersionedOutputName = objects.property(Boolean.class).value(false);
        this.shouldForceRebuild = objects.property(Boolean.class).value(false);
        this.outputFilename = objects.property(String.class).value("");
        this.renameOutput = objects.mapProperty(String.class, String.class).value(new HashMap<>());
    }

    @Override
    public Property<Boolean> getNoWarn() {
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

    @Override
    public Property<String> getOutputFilename() {
        return outputFilename;
    }

    @Override
    public MapProperty<String, String> getRenameOutput() {
        return renameOutput;
    }
}
