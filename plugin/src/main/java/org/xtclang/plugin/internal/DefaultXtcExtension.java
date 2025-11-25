package org.xtclang.plugin.internal;

import javax.inject.Inject;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcExtension;

import static org.xtclang.plugin.XtcPluginConstants.PROPERTY_VERBOSE_LOGGING_OVERRIDE;

@SuppressWarnings("this-escape") // Safe: calling abstract getter in constructor to set convention is standard Gradle pattern
public abstract class DefaultXtcExtension implements XtcExtension {
    private final String projectName;

    @Inject
    public DefaultXtcExtension(final ObjectFactory objects, final ProviderFactory providers) {
        this.projectName = providers.gradleProperty("name").getOrElse("unknown");

        // Set convention from project property
        getVerboseLogging().convention(
            providers.gradleProperty(PROPERTY_VERBOSE_LOGGING_OVERRIDE)
                .map(Boolean::parseBoolean)
                .orElse(false)
        );
    }

    @Override
    public abstract Property<@NotNull Boolean> getVerboseLogging();

    @Override
    public String toString() {
        return projectName + " XTC extension";
    }
}
