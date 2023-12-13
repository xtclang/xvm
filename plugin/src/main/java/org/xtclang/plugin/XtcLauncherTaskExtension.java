package org.xtclang.plugin;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import java.io.InputStream;
import java.io.OutputStream;

@SuppressWarnings("unused")
public interface XtcLauncherTaskExtension {
    Property<Boolean> getFork();

    Property<Boolean> getShowVersion();

    Property<Boolean> getUseNativeLauncher();

    Property<Boolean> getVerbose();

    ListProperty<String> getJvmArgs();

    Property<InputStream> getStdin();

    Property<OutputStream> getStdout();

    Property<OutputStream> getStderr();

    default void jvmArg(String arg) {
        jvmArgs(arg);
    }

    void jvmArg(Provider<? extends String> arg);

    void jvmArgs(String... args);

    void jvmArgs(Iterable<? extends String> args);

    void jvmArgs(Provider<? extends Iterable<? extends String>> provider);

    void setJvmArgs(Iterable<? extends String> elements);

    void setJvmArgs(Provider<? extends Iterable<? extends String>> provider);
}