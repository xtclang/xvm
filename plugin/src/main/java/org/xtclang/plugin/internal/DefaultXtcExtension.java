package org.xtclang.plugin.internal;

import javax.inject.Inject;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcExtension;

import static org.xtclang.plugin.XtcPluginConstants.PROPERTY_VERBOSE_LOGGING_OVERRIDE;

public abstract class DefaultXtcExtension implements XtcExtension {
    private final Property<@NotNull Boolean> verboseLogging;

    @Inject
    @SuppressWarnings("ConstructorNotProtectedInAbstractClass")
    public DefaultXtcExtension(final ObjectFactory objects, final ProviderFactory providers) {
        this.verboseLogging = objects.property(Boolean.class).convention(
            providers.gradleProperty(PROPERTY_VERBOSE_LOGGING_OVERRIDE)
                .map(Boolean::parseBoolean)
                .orElse(false)
        );
    }

    @Override
    public Property<@NotNull Boolean> getVerboseLogging() {
        return verboseLogging;
    }
}
