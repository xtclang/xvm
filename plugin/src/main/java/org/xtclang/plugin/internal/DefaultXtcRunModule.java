package org.xtclang.plugin.internal;

import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.xtclang.plugin.XtcPluginUtils;
import org.xtclang.plugin.XtcRunModule;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

public class DefaultXtcRunModule implements XtcRunModule {
    private static final String DEFAULT_METHOD_NAME = "run";

    private final Property<String> moduleName; // mandatory
    private final Property<String> methodName;
    private final ListProperty<String> moduleArgs;

    @Inject
    @SuppressWarnings("unused")
    public DefaultXtcRunModule(final Project project) {
        this(project, null);
    }

    public DefaultXtcRunModule(final Project project, final String moduleName) {
        this(project, moduleName, DEFAULT_METHOD_NAME, emptyList());
    }

    public DefaultXtcRunModule(final Project project, final String moduleName, final String moduleMethod, final List<String> moduleArgs) {
        final var objects = requireNonNull(project.getObjects());
        this.moduleName = objects.property(String.class);
        this.methodName = objects.property(String.class).value(requireNonNull(moduleMethod));
        this.moduleArgs = objects.listProperty(String.class).value(new ArrayList<>(moduleArgs));
        if (moduleName != null) {
            this.moduleName.set(moduleName);
        }
    }

    @Deprecated(since = "Figure out a better way to override/resolve dependencies for the run configurations and the tasks that inherit it.")
    static List<Object> getModuleInputs(final XtcRunModule module) {
        return List.of(module.getModuleName(), module.getMethodName(), module.getModuleArgs());
    }

    @Override
    public Property<String> getModuleName() {
        return moduleName;
    }

    @Override
    public Property<String> getMethodName() {
        return methodName;
    }

    @Override
    public ListProperty<String> getModuleArgs() {
        return moduleArgs;
    }

    @Override
    public void moduleArgs(final Object... args) {
        moduleArgs(XtcPluginUtils.argumentArrayToList(args));
    }

    @Override
    public void moduleArgs(final Iterable<? extends String> args) {
        moduleArgs.addAll(args);
    }

    @Override
    public void moduleArgs(final Provider<? extends Iterable<? extends String>> provider) {
        moduleArgs.addAll(provider);
    }

    @Override
    public void setModuleArgs(final Iterable<? extends String> args) {
        moduleArgs.set(args);
    }

    @Override
    public void setModuleArgs(final Provider<? extends Iterable<? extends String>> provider) {
        moduleArgs.set(provider);
    }

    @Override
    public boolean hasDefaultMethodName() {
        return DEFAULT_METHOD_NAME.equals(getMethodName().get());
    }

    @Override
    public String getDefaultMethodName() {
        return DEFAULT_METHOD_NAME;
    }

    @Override
    public boolean validate() {
        return moduleName.isPresent() && methodName.isPresent();
    }

    @Override
    public String toString() {
        return "[xtcRunModule: moduleName='" + (moduleName.isPresent() ? moduleName.get() : "NONE") + "', methodName='" + (methodName.isPresent() ? methodName.get() : "NONE") + "', methodArgs='" + getModuleArgs() + "']";
    }
}
