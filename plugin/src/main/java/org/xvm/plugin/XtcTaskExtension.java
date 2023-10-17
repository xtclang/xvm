package org.xvm.plugin;

import org.gradle.api.provider.ListProperty;

public interface XtcTaskExtension {
    // Since we are using Properties/Providers we don't need an explicit setter per se.
    ListProperty<String> getJvmArgs();

    XtcTaskExtension jvmArgs(Object... jvmArgs);

    XtcTaskExtension jvmArgs(Iterable<?> jvmArgs);

    XtcTaskExtension setJvmArgs(Object... jvmArgs);

    XtcTaskExtension setJvmArgs(Iterable<?> jvmArgs);
}

