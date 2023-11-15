package org.xvm.plugin;

import org.gradle.api.Action;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import java.util.List;

public interface XtcRuntimeExtension extends XtcTaskExtension {

    String DEFAULT_METHOD_NAME = "run";

    interface XtcRunModule {
        Property<String> getModuleName();

        Property<String> getMethodName();

        ListProperty<String> getArgs();

        void args(List<String> args);

        void args(Object... args);

        default boolean validate() {
            return true;
        }

        default boolean hasDefaultMethodName() {
            return DEFAULT_METHOD_NAME.equals(getMethodName().get());
        }
    }

    Property<Boolean> getShowVersion();

    Property<Boolean> getAllowParallel();

    Property<Boolean> getDebugEnabled();

    XtcRunModule module(Action<XtcRunModule> action);

    void moduleName(String name);

    XtcRuntimeExtension moduleNames(String... modules);

    ListProperty<XtcRunModule> getModules();

    XtcRuntimeExtension setModules(List<XtcRunModule> modules);

    XtcRuntimeExtension setModuleNames(List<String> moduleNames);

    XtcRuntimeExtension setModuleNames(String... moduleNames);

    default boolean validateModules() {
        return true;
    }
}
