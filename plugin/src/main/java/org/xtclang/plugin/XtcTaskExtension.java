package org.xtclang.plugin;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

@SuppressWarnings("unused")
public interface XtcTaskExtension {
    // TODO: Increase granularity for this later, so that we can control individual module execution fork policies from the run tasks?
    //   (the easiest solution, would likely be to just add setters that manipulate the extension values in the tasks, or maybe resetting them)
    // TODO: This may be a current source of confusion - allowing a task property getter, and changing its value, will lead to the entire extension for
    //   all tasks of that kind being changed, and that is something we should definitely fix.
    Property<Boolean> getFork();

    Property<Boolean> getUseNativeLauncher();

    Property<Boolean> getLogOutputs();

    Property<Boolean> getVerbose();

    ListProperty<String> getJvmArgs();

    XtcTaskExtension jvmArgs(Object... jvmArgs);

    XtcTaskExtension jvmArgs(Iterable<?> jvmArgs);

    XtcTaskExtension setJvmArgs(Object... jvmArgs);

    XtcTaskExtension setJvmArgs(Iterable<?> jvmArgs);
}
