package org.xtclang.plugin;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public interface XtcTaskExtension {
    ListProperty<String> getJvmArgs();

    Property<Boolean> getFork();

    Property<Boolean> getUseNativeLauncher();

    Property<Boolean> getVerbose();

    XtcTaskExtension jvmArgs(Object... jvmArgs);

    XtcTaskExtension jvmArgs(Iterable<?> jvmArgs);

    XtcTaskExtension setJvmArgs(Object... jvmArgs);

    XtcTaskExtension setJvmArgs(Iterable<?> jvmArgs);
}
