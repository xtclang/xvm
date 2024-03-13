package org.xtclang.plugin.internal;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;

import org.xtclang.plugin.XtcCompilerExtension;

public class DefaultXtcCompilerExtension extends DefaultXtcLauncherTaskExtension implements XtcCompilerExtension {
    private static final String REBUILD_FLAG_DEFAULT_PROPERTY = "xtcDefaultRebuild";

    // Even though we have getters, these need to be protected for subclasses to be able to use them in the build DSL
    // We still have to have getters, or the plugin validation fails, and we might as well put the input properties on
    // those for readability.
    protected final Property<Boolean> disableWarnings;
    protected final Property<Boolean> strict;
    protected final Property<Boolean> hasQualifiedOutputName;
    protected final Property<Boolean> hasVersionedOutputName;
    protected final Property<String> stamp;
    protected final Property<Boolean> rebuild;

    @Inject
    public DefaultXtcCompilerExtension(final Project project) {
        super(project);

        final var rebuildDefaultProperty = project.findProperty(REBUILD_FLAG_DEFAULT_PROPERTY);
        final var rebuildDefaultValue = rebuildDefaultProperty == null || Boolean.parseBoolean(rebuildDefaultProperty.toString());
        if (!rebuildDefaultValue) {
            logger.info("{} Project has global override for default value of rebuild flag: false", prefix);
        }
        this.rebuild = objects.property(Boolean.class).convention(rebuildDefaultValue);

        this.disableWarnings = objects.property(Boolean.class).convention(false);
        this.strict = objects.property(Boolean.class).convention(false);
        this.hasQualifiedOutputName = objects.property(Boolean.class).convention(false);
        this.hasVersionedOutputName = objects.property(Boolean.class).convention(false);
        this.stamp = objects.property(String.class);
    }

    @Override
    public Property<Boolean> getDisableWarnings() {
        return disableWarnings;
    }

    @Override
    public Property<Boolean> getStrict() {
        return strict;
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
    public Property<Boolean> getRebuild() {
        return rebuild;
    }
}
