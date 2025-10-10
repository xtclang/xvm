package org.xtclang.plugin.internal;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.ProjectDelegate;
import org.xtclang.plugin.XtcCompilerExtension;

public class DefaultXtcCompilerExtension extends DefaultXtcLauncherTaskExtension implements XtcCompilerExtension {
    private static final String REBUILD_FLAG_DEFAULT_PROPERTY = "xtcDefaultRebuild";

    // Even though we have getters, these need to be protected for subclasses to be able to use them in the build DSL
    // We still have to have getters, or the plugin validation fails, and we might as well put the input properties on
    // those for readability.
    protected final Property<@NotNull Boolean> disableWarnings;
    protected final Property<@NotNull Boolean> strict;
    protected final Property<@NotNull Boolean> hasQualifiedOutputName;
    protected final Property<@NotNull Boolean> hasVersionedOutputName;
    protected final Property<@NotNull String> stamp;
    protected final Property<@NotNull Boolean> rebuild;

    @Inject
    public DefaultXtcCompilerExtension(final Project project) {
        super(project);

        // Capture project property at configuration time to avoid Project references during execution
        final var rebuildDefaultProperty = project.findProperty(REBUILD_FLAG_DEFAULT_PROPERTY);
        final var rebuildDefaultValue = rebuildDefaultProperty == null || Boolean.parseBoolean(rebuildDefaultProperty.toString());
        if (!rebuildDefaultValue) {
            logger.info("[plugin] Project has global override for default value of rebuild flag: false");
        }
        this.rebuild = objects.property(Boolean.class).convention(rebuildDefaultValue);

        this.disableWarnings = objects.property(Boolean.class).convention(false);
        this.strict = objects.property(Boolean.class).convention(false);
        this.hasQualifiedOutputName = objects.property(Boolean.class).convention(false);
        this.hasVersionedOutputName = objects.property(Boolean.class).convention(false);

        // Set default xtcVersion from plugin-build-info.properties
        final var defaultXdkVersion = ProjectDelegate.readXdkVersion();
        this.stamp = objects.property(String.class).convention(defaultXdkVersion);
    }

    @Override
    public Property<@NotNull Boolean> getDisableWarnings() {
        return disableWarnings;
    }

    @Override
    public Property<@NotNull Boolean> getStrict() {
        return strict;
    }

    @Override
    public Property<@NotNull Boolean> getQualifiedOutputName() {
        return hasQualifiedOutputName;
    }

    @Override
    public Property<@NotNull Boolean> getVersionedOutputName() {
        return hasVersionedOutputName;
    }

    @Override
    public Property<@NotNull String> getXtcVersion() {
        return stamp;
    }

    @Override
    public Property<@NotNull Boolean> getRebuild() {
        return rebuild;
    }
}
