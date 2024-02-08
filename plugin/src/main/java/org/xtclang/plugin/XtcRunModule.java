package org.xtclang.plugin;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

public interface XtcRunModule extends Comparable<XtcRunModule> {
    Property<String> getModuleName();

    Property<String> getMethodName();

    ListProperty<String> getModuleArgs();

    default void moduleArg(final String arg) {
        moduleArgs(arg);
    }

    void moduleArg(final Provider<? extends String> provider);

    void moduleArgs(String... args);

    void moduleArgs(Iterable<? extends String> args);

    void moduleArgs(Provider<? extends Iterable<? extends String>> provider);

    void setModuleArgs(Iterable<? extends String> args);

    void setModuleArgs(Provider<? extends Iterable<? extends String>> provider);

    boolean hasDefaultMethodName();

    boolean validate();

    String toString(boolean mayResolveProviders);
}
