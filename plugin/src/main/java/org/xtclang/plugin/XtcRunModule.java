package org.xtclang.plugin;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

public interface XtcRunModule {
    Property<String> getModuleName();

    Property<String> getMethodName();

    ListProperty<String> getModuleArgs();

    void moduleArgs(String... args);

    void moduleArgs(Iterable<? extends String> args);

    void moduleArgs(Provider<? extends Iterable<? extends String>> provider);

    void setModuleArgs(Iterable<? extends String> args);

    void setModuleArgs(Provider<? extends Iterable<? extends String>> provider);

    boolean hasDefaultMethodName();

    String getDefaultMethodName();

    boolean validate();
}
