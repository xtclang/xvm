package org.xtclang.plugin.internal;

import javax.inject.Inject;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcTestExtension;

/**
 * Default implementation of XtcTestExtension. Extends the runtime extension
 * with test-specific properties.
 */
public abstract class DefaultXtcTestExtension extends DefaultXtcRuntimeExtension implements XtcTestExtension {
    private final Property<@NotNull Boolean> failOnTestFailure;
    private final ListProperty<@NotNull String> includes;
    private final ListProperty<@NotNull String> excludes;

    @Inject
    @SuppressWarnings("ConstructorNotProtectedInAbstractClass")
    public DefaultXtcTestExtension(final ObjectFactory objects, final ProviderFactory providers) {
        super(objects, providers);
        this.failOnTestFailure = objects.property(Boolean.class).convention(true);
        this.includes = objects.listProperty(String.class).empty();
        this.excludes = objects.listProperty(String.class).empty();
    }

    @Override
    public Property<@NotNull Boolean> getFailOnTestFailure() {
        return failOnTestFailure;
    }

    @Override
    public ListProperty<@NotNull String> getIncludes() {
        return includes;
    }

    @Override
    public ListProperty<@NotNull String> getExcludes() {
        return excludes;
    }
}
