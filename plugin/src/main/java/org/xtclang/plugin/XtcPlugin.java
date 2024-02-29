package org.xtclang.plugin;

import java.util.Set;

import javax.inject.Inject;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.SoftwareComponentFactory;
import org.gradle.api.plugins.JavaBasePlugin;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public class XtcPlugin implements Plugin<Project> {
    /**
     * Software component for an XTC project, equivalent to components["java"], used e.g. for publishing
     */
    private static final Set<Class<?>> REQUIRED_PLUGINS = Set.of(JavaBasePlugin.class, XtcProjectPlugin.class);

    @Override
    public void apply(final Project project) {
        final var plugins = project.getPluginManager();
        REQUIRED_PLUGINS.forEach(plugins::apply);
    }

    /**
     * A project scoped XTC plugin. Delegates is state on apply to a project
     * aware plugin delegate, where the main plugin logic exists. This is to
     * maximize final state, and not have to request project information through
     * the Gradle APIs, everywhere.
     */
    static class XtcProjectPlugin implements Plugin<Project> {
        private final AdhocComponentWithVariants xtcComponent;

        @Inject
        XtcProjectPlugin(final SoftwareComponentFactory softwareComponentFactory) {
            this.xtcComponent = softwareComponentFactory.adhoc(XtcPluginConstants.XTC_COMPONENT_NAME);
        }

        @Override
        public void apply(final @NotNull Project project) {
            new XtcProjectDelegate(project, xtcComponent).apply();
        }
    }

}
