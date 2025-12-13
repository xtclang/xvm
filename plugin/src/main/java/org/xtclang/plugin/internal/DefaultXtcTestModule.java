package org.xtclang.plugin.internal;

import java.util.List;

import javax.inject.Inject;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcPluginUtils;
import org.xtclang.plugin.XtcTestModule;

public class DefaultXtcTestModule implements XtcTestModule {
    private static final String DEFAULT_METHOD_NAME = "run";

    protected final Property<@NotNull String> moduleName;
    protected final ListProperty<@NotNull String> moduleArgs;

    private final ObjectFactory objects;

    @Inject
    public DefaultXtcTestModule(final ObjectFactory objects) {
        this.objects = objects;
        this.moduleName = objects.property(String.class);
        this.moduleArgs = objects.listProperty(String.class).empty();
    }

    @Deprecated //TODO: Figure out a better way to override/resolve dependencies for the run configurations and the tasks that inherit it.
    static List<Object> getModuleInputs(final XtcTestModule module) {
        return List.of(module.getModuleName(), module.getModuleArgs());
    }

    @Override
    public Property<@NotNull String> getModuleName() {
        return moduleName;
    }

    @Override
    public ListProperty<@NotNull String> getModuleArgs() {
        return moduleArgs;
    }

    @Override
    public void moduleArg(final Provider<? extends @NotNull String> arg) {
        moduleArgs(objects.listProperty(String.class).value(arg.map(java.util.Collections::singletonList)));
    }

    @Override
    public void moduleArgs(final String... args) {
        moduleArgs(XtcPluginUtils.argumentArrayToList(args));
    }

    @Override
    public void moduleArgs(final Iterable<? extends String> args) {
        moduleArgs.addAll(args);
    }

    @Override
    public void moduleArgs(final Provider<? extends @NotNull Iterable<? extends String>> provider) {
        moduleArgs.addAll(provider);
    }

    @Override
    public void setModuleArgs(final Iterable<? extends String> args) {
        moduleArgs.set(args);
    }

    @Override
    public void setModuleArgs(final Provider<? extends @NotNull Iterable<? extends String>> provider) {
        moduleArgs.set(provider);
    }

    @Override
    public boolean validate() {
        return moduleName.isPresent();
    }

    public static String getDefaultMethodName() {
        return DEFAULT_METHOD_NAME;
    }

    @Override
    public int compareTo(final XtcTestModule other) {
        return getModuleName().get().compareTo(other.getModuleName().get());
    }

    @Override
    public String toString() {
        return toString(false);
    }

    @Override
    public String toString(final boolean mayResolveProviders) {
        return '['
                + getClass().getSimpleName()
                + ": moduleName='" + (moduleName.isPresent() ? moduleName.get() : "NONE")
                + "', moduleArgs='" + (mayResolveProviders ? getModuleArgs().get() : getModuleArgs())
                + "']";
    }
}
