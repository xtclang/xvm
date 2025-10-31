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

    /**
     * Whether to use the compiler daemon (Build Service) for compilation.
     * When enabled, the XTC compiler runs in a persistent daemon process
     * that is reused across all compilation tasks, eliminating JVM startup overhead.
     *
     * <p>Benefits of using the compiler daemon:
     * <ul>
     *   <li>No JVM startup overhead for each compilation</li>
     *   <li>ClassLoader and class metadata reused across compilations</li>
     *   <li>JIT compilation benefits from warmed-up code paths</li>
     * </ul>
     *
     * <p>When the daemon is disabled (useCompilerDaemon=false), the plugin falls back
     * to forking a new Java process for each compilation (JavaExecLauncher), providing
     * maximum isolation at the cost of JVM startup overhead.
     *
     * <p>Default: true (recommended for best performance)
     *
     * <p><b>Debugging:</b> To debug the compiler, use standard JDWP arguments via jvmArgs:
     * <pre>
     * jvmArgs("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
     * </pre>
     *
     * @return property controlling whether to use the compiler daemon
     */
    Property<@NotNull Boolean> getUseCompilerDaemon();

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
