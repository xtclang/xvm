package org.xtclang.plugin;

import java.io.InputStream;
import java.io.OutputStream;

import org.gradle.api.Named;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public interface XtcLauncherTaskExtension extends Named {
    Property<@NotNull Boolean> getFork();

    Property<@NotNull Boolean> getShowVersion();

    Property<@NotNull Boolean> getUseNativeLauncher();

    Property<@NotNull Boolean> getVerbose();

    ListProperty<@NotNull String> getJvmArgs();

    Property<@NotNull InputStream> getStdin();

    Property<@NotNull OutputStream> getStdout();

    Property<@NotNull OutputStream> getStderr();

    Property<@NotNull Boolean> getDebug();

    Property<@NotNull Integer> getDebugPort();

    Property<@NotNull Boolean> getDebugSuspend();

    default void jvmArg(final String arg) {
        jvmArgs(arg);
    }

    void jvmArg(Provider<? extends @NotNull String> arg);

    void jvmArgs(String... args);

    void jvmArgs(Iterable<? extends String> args);

    void jvmArgs(Provider<? extends @NotNull Iterable<? extends String>> provider);

    void setJvmArgs(Iterable<? extends String> elements);

    void setJvmArgs(Provider<? extends @NotNull Iterable<? extends String>> provider);

    @Override
    default @NotNull String getName() {
        return getClass().getSimpleName();
    }
}
