package org.xtclang.plugin.internal;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcPluginUtils;
import org.xtclang.plugin.XtcRunModule;

public class DefaultXtcRunModule implements XtcRunModule {
    public static final String DEFAULT_METHOD_NAME = "run";

    protected final Property<@NotNull String> moduleName;
    protected final Property<@NotNull String> methodName;
    protected final ListProperty<@NotNull String> moduleArgs;

    private final ObjectFactory objects;

    @Inject
    public DefaultXtcRunModule(final ObjectFactory objects) {
        this.objects = objects;
        this.moduleName = objects.property(String.class);
        this.methodName = objects.property(String.class).convention(DEFAULT_METHOD_NAME);
        this.moduleArgs = objects.listProperty(String.class).empty();
    }

    @Deprecated //TODO: Figure out a better way to override/resolve dependencies for the run configurations and the tasks that inherit it.
    static List<Object> getModuleInputs(final XtcRunModule module) {
        return List.of(module.getModuleName(), module.getMethodName(), module.getModuleArgs());
    }

    @Override
    public Property<@NotNull String> getModuleName() {
        return moduleName;
    }

    @Override
    public Property<@NotNull String> getMethodName() {
        return methodName;
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
    public boolean hasDefaultMethodName() {
        return getDefaultMethodName().equals(getMethodName().get());
    }

    @Override
    public boolean validate() {
        return moduleName.isPresent() && methodName.isPresent();
    }

    public static String getDefaultMethodName() {
        return DEFAULT_METHOD_NAME;
    }

    @Override
    public int compareTo(final XtcRunModule other) {
        return getModuleName().get().compareTo(other.getModuleName().get());
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof DefaultXtcRunModule && compareTo((DefaultXtcRunModule) o) == 0;
    }

    @Override
    public int hashCode() {
        return moduleName.hashCode() ^ methodName.hashCode();
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
            + "', methodName='" + (methodName.isPresent() ? methodName.get() : "NONE")
            + "', moduleArgs='" + (mayResolveProviders ? getModuleArgs().get() : getModuleArgs())
            + "']";
    }
}
