package org.xvm.plugin;

import org.gradle.api.Action;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import java.util.List;

public interface XtcRuntimeExtension extends XtcTaskExtension {

    String DEFAULT_METHOD_NAME = "run";

    interface XtcRunModule {
        Property<String> getModuleName();

        Property<String> getMethod();

        ListProperty<String> getArgs();

        default boolean validate() {
            return true;
        }

        default boolean hasDefaultMethodName() {
            return DEFAULT_METHOD_NAME.equals(getMethod().get());
        }
    }

    Property<Boolean> getUseNativeLauncher();

    Property<Boolean> getVerbose();

    Property<Boolean> getShowVersion();

    Property<Boolean> getAllowParallel();

    Property<Boolean> getDebugEnabled();

    XtcRunModule module(Action<XtcRunModule> action);

    XtcRunModule moduleName(String name);

    XtcRuntimeExtension moduleNames(String... modules);

    ListProperty<XtcRunModule> getModules();

    XtcRuntimeExtension setModules(List<XtcRunModule> modules);

    XtcRuntimeExtension setModuleNames(List<String> moduleNames);

    XtcRuntimeExtension setModuleNames(String... moduleNames);

    default boolean validateModules() {
        return true;
    }
}
