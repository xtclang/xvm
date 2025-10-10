package org.xtclang.plugin;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import org.jetbrains.annotations.NotNull;

public interface XtcRunModule extends Comparable<XtcRunModule> {
    Property<@NotNull String> getModuleName();

    Property<@NotNull String> getMethodName();

    ListProperty<@NotNull String> getModuleArgs();

    default void moduleArg(final String arg) {
        moduleArgs(arg);
    }

    void moduleArg(Provider<? extends @NotNull String> provider);

    void moduleArgs(String... args);

    void moduleArgs(Iterable<? extends String> args);

    void moduleArgs(Provider<? extends @NotNull Iterable<? extends String>> provider);

    @SuppressWarnings("unused")
    void setModuleArgs(Iterable<? extends String> args);

    @SuppressWarnings("unused")
    void setModuleArgs(Provider<? extends @NotNull Iterable<? extends String>> provider);

    boolean hasDefaultMethodName();

    boolean validate();

    String toString(boolean mayResolveProviders);
}
