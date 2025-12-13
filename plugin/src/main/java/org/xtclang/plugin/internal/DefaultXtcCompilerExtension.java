package org.xtclang.plugin.internal;

import javax.inject.Inject;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcCompilerExtension;
import org.xtclang.plugin.XtcProjectDelegate;

public abstract class DefaultXtcCompilerExtension extends DefaultXtcLauncherTaskExtension implements XtcCompilerExtension {
    private static final Logger LOGGER = Logging.getLogger(DefaultXtcCompilerExtension.class);
    private static final String REBUILD_FLAG_DEFAULT_PROPERTY = "xtcDefaultRebuild";

    protected final Property<@NotNull Boolean> disableWarnings;
    protected final Property<@NotNull Boolean> strict;
    protected final Property<@NotNull Boolean> hasQualifiedOutputName;
    protected final Property<@NotNull Boolean> hasVersionedOutputName;
    protected final Property<@NotNull String> stamp;
    protected final Property<@NotNull Boolean> rebuild;

    @Inject
    @SuppressWarnings("ConstructorNotProtectedInAbstractClass")
    public DefaultXtcCompilerExtension(final ObjectFactory objects, final ProviderFactory providers) {
        super(objects, providers);
        final Provider<@NotNull String> rebuildDefaultProperty = providers.gradleProperty(REBUILD_FLAG_DEFAULT_PROPERTY);
        final boolean rebuildDefaultValue = !rebuildDefaultProperty.isPresent() || Boolean.parseBoolean(rebuildDefaultProperty.get());
        if (!rebuildDefaultValue) {
            LOGGER.info("[plugin] Project has global override for default value of rebuild flag: false");
        }
        this.rebuild = objects.property(Boolean.class).convention(rebuildDefaultValue);
        this.disableWarnings = objects.property(Boolean.class).convention(false);
        this.strict = objects.property(Boolean.class).convention(false);
        this.hasQualifiedOutputName = objects.property(Boolean.class).convention(false);
        this.hasVersionedOutputName = objects.property(Boolean.class).convention(false);
        this.stamp = objects.property(String.class).convention(XtcProjectDelegate.readXdkVersion());
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
