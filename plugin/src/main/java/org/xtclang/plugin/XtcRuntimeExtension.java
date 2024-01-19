package org.xtclang.plugin;

import org.gradle.api.Action;
import org.gradle.api.provider.ListProperty;

import java.util.List;

@SuppressWarnings("unused") // TODO Implement and code coverage test all configurations.
public interface XtcRuntimeExtension extends XtcLauncherTaskExtension {

    XtcRunModule module(Action<XtcRunModule> action);

    ListProperty<XtcRunModule> getModules();

    void moduleName(String name);

    void moduleNames(String... modules);

    void setModules(List<XtcRunModule> modules);

    void setModules(XtcRunModule... modules);

    void setModuleNames(List<String> moduleNames);

    void setModuleNames(String... moduleNames);

    boolean isEmpty();
}
