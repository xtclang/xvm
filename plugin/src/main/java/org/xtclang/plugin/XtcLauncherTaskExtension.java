package org.xtclang.plugin;

import java.io.InputStream;
import java.io.OutputStream;

import org.gradle.api.Named;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")
public interface XtcLauncherTaskExtension extends Named {
    /**
     * Additional module path entries (files or directories) to add to the -L flag.
     * This allows specifying custom locations for XTC modules beyond the default
     * source set outputs and xtcModule dependencies.
     *
     * @return the configurable file collection for the module path
     */
    ConfigurableFileCollection getModulePath();

    Property<@NotNull Boolean> getFork();

    Property<@NotNull Boolean> getShowVersion();

    Property<@NotNull Boolean> getUseNativeLauncher();

    Property<@NotNull Boolean> getVerbose();

    ListProperty<@NotNull String> getJvmArgs();

    Property<@NotNull InputStream> getStdin();

    Property<@NotNull OutputStream> getStdout();

    Property<@NotNull OutputStream> getStderr();

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
