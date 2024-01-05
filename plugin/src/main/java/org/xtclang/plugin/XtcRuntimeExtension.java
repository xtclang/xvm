package org.xtclang.plugin;

import org.gradle.api.Action;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import java.util.List;

@SuppressWarnings("unused") // TODO Implement and code coverage test all configurations.
public interface XtcRuntimeExtension extends XtcTaskExtension {

    String DEFAULT_METHOD_NAME = "run";

    interface XtcRunModule {
        Property<String> getModuleName();

        Property<String> getMethodName();

        List<Provider<String>> getModuleArgs();

        List<String> resolveModuleArgs();

        void setModuleArgs(Iterable<?> args);

        void setModuleArgs(Object... args);

        void moduleArgs(Iterable<?> args);

        void moduleArgs(Object... args);

        default boolean hasDefaultMethodName() {
            return DEFAULT_METHOD_NAME.equals(getMethodName().get());
        }

        boolean validate();
    }

    Property<Boolean> getShowVersion();

    Property<Boolean> getAllowParallel();

    Property<Boolean> getDebugEnabled();

    XtcRunModule module(Action<XtcRunModule> action);

    ListProperty<XtcRunModule> getModules();

    void moduleName(String name);

    XtcRuntimeExtension moduleNames(String... modules);

    XtcRuntimeExtension setModules(List<XtcRunModule> modules);

    XtcRuntimeExtension setModules(XtcRunModule... modules);

    XtcRuntimeExtension setModuleNames(List<String> moduleNames);

    XtcRuntimeExtension setModuleNames(String... moduleNames);

    ListProperty<Object> getModuleInputs();

    List<XtcRunModule> validatedModules();

    boolean isEmpty();

    List<String> getModuleNames();

    List<String> getModuleMethods();

    List<String> getModuleArgs();
}