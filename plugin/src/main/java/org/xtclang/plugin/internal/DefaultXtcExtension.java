package org.xtclang.plugin.internal;

import javax.inject.Inject;

import org.gradle.api.provider.ProviderFactory;

import org.xtclang.plugin.XtcExtension;

@SuppressWarnings("ClassCanBeRecord")
public class DefaultXtcExtension implements XtcExtension {
    private final String projectName;

    @Inject
    public DefaultXtcExtension(final ProviderFactory providers) {
        this.projectName = providers.gradleProperty("name").getOrElse("unknown");
    }

    @Override
    public String toString() {
        return projectName + " XTC extension";
    }
}
