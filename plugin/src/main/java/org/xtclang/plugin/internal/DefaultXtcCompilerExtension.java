package org.xtclang.plugin.internal;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.xtclang.plugin.XtcCompilerExtension;

import javax.inject.Inject;

public class DefaultXtcCompilerExtension extends DefaultXtcLauncherTaskExtension implements XtcCompilerExtension {
    // Even though we have getters, these need to be protected for subclasses to be able to use them in the build DSL
    // We still have to have getters, or the plugin validation fails, and we might as well put the input properties on
    // those for readability.
    protected final Property<Boolean> disableWarnings;
    protected final Property<Boolean> isStrict;
    protected final Property<Boolean> hasQualifiedOutputName;
    protected final Property<Boolean> hasVersionedOutputName;
    protected final Property<String> stamp;
    protected final Property<Boolean> shouldForceRebuild;

    @Inject
    public DefaultXtcCompilerExtension(final Project project) {
        super(project);
        this.disableWarnings = objects.property(Boolean.class).convention(false);
        this.isStrict = objects.property(Boolean.class).convention(false);
        this.hasQualifiedOutputName = objects.property(Boolean.class).convention(false);
        this.hasVersionedOutputName = objects.property(Boolean.class).convention(false);
        this.stamp = objects.property(String.class);
        this.shouldForceRebuild = objects.property(Boolean.class).convention(false);
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
    public Property<String> getXtcVersion() {
        return stamp;
    }

    @Override
    public Property<Boolean> getForceRebuild() {
        return shouldForceRebuild;
    }
}
