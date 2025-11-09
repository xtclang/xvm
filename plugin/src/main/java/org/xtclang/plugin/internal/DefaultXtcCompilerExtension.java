package org.xtclang.plugin.internal;

import javax.inject.Inject;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.ProjectDelegate;
import org.xtclang.plugin.XtcCompilerExtension;

public abstract class DefaultXtcCompilerExtension extends DefaultXtcLauncherTaskExtension implements XtcCompilerExtension {
    private static final Logger logger = Logging.getLogger(DefaultXtcCompilerExtension.class);
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
    @SuppressWarnings("this-escape") // Calling @Inject abstract method in constructor is safe
    public DefaultXtcCompilerExtension() {
        final ObjectFactory objects = getObjects();
        final ProviderFactory providers = getProviders();
        final Provider<@NotNull String> rebuildDefaultProperty = providers.gradleProperty(REBUILD_FLAG_DEFAULT_PROPERTY);
        final boolean rebuildDefaultValue = !rebuildDefaultProperty.isPresent() || Boolean.parseBoolean(rebuildDefaultProperty.get());
        if (!rebuildDefaultValue) {
            logger.info("[plugin] Project has global override for default value of rebuild flag: false");
        }
        this.rebuild = objects.property(Boolean.class).convention(rebuildDefaultValue);
        this.disableWarnings = objects.property(Boolean.class).convention(false);
        this.strict = objects.property(Boolean.class).convention(false);
        this.hasQualifiedOutputName = objects.property(Boolean.class).convention(false);
        this.hasVersionedOutputName = objects.property(Boolean.class).convention(false);
        this.stamp = objects.property(String.class).convention(ProjectDelegate.readXdkVersion());
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
