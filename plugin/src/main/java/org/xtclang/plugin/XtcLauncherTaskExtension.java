package org.xtclang.plugin;

import java.io.File;

import org.gradle.api.Named;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFile;
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

    Property<@NotNull Boolean> getVerbose();

    ListProperty<@NotNull String> getJvmArgs();

    /**
     * File path pattern for stdout redirection. Supports timestamp placeholder %TIMESTAMP%
     * which expands to yyyyMMddHHmmss format at execution time.
     *
     * <p>Can be set in multiple ways:
     * <pre>
     * // Direct string (supports %TIMESTAMP% placeholder)
     * stdoutPath.set("build/logs/compiler-%TIMESTAMP%.log")
     *
     * // Provider from layout (lazy, configuration cache compatible)
     * stdoutPath.set(layout.buildDirectory.file("logs/output.log").map { it.asFile.absolutePath })
     *
     * // Convenience method with File
     * stdoutPath(project.file("my-output.log"))
     *
     * // Convenience method with Provider&lt;RegularFile&gt;
     * stdoutPath(layout.buildDirectory.file("logs/output.log"))
     * </pre>
     *
     * If not set, stdout will be inherited from the parent process (normal fork)
     * or redirected to a default timestamped log file (detached mode).
     *
     * @return the stdout file path pattern property
     */
    Property<@NotNull String> getStdoutPath();

    /**
     * Convenience method to set stdout from a File.
     * @param file The file to redirect stdout to
     */
    default void stdoutPath(final File file) {
        getStdoutPath().set(file.getAbsolutePath());
    }

    /**
     * Convenience method to set stdout from a Provider&lt;RegularFile&gt;.
     * Useful with layout.buildDirectory.file("path").
     * @param fileProvider The file provider to redirect stdout to
     */
    default void stdoutPath(final Provider<@NotNull RegularFile> fileProvider) {
        getStdoutPath().set(fileProvider.map(f -> f.getAsFile().getAbsolutePath()));
    }

    /**
     * File path pattern for stderr redirection. Supports timestamp placeholder %TIMESTAMP%
     * which expands to yyyyMMddHHmmss format at execution time.
     *
     * <p>Can be set in multiple ways:
     * <pre>
     * // Direct string (supports %TIMESTAMP% placeholder)
     * stderrPath.set("build/logs/errors-%TIMESTAMP%.log")
     *
     * // Provider from layout (lazy, configuration cache compatible)
     * stderrPath.set(layout.buildDirectory.file("logs/errors.log").map { it.asFile.absolutePath })
     *
     * // Convenience method with File
     * stderrPath(project.file("my-errors.log"))
     *
     * // Convenience method with Provider&lt;RegularFile&gt;
     * stderrPath(layout.buildDirectory.file("logs/errors.log"))
     * </pre>
     *
     * If not set, stderr will be inherited from the parent process (normal fork)
     * or redirected to a default timestamped log file (detached mode).
     *
     * @return the stderr file path pattern property
     */
    Property<@NotNull String> getStderrPath();

    /**
     * Convenience method to set stderr from a File.
     * @param file The file to redirect stderr to
     */
    default void stderrPath(final File file) {
        getStderrPath().set(file.getAbsolutePath());
    }

    /**
     * Convenience method to set stderr from a Provider&lt;RegularFile&gt;.
     * Useful with layout.buildDirectory.file("path").
     * @param fileProvider The file provider to redirect stderr to
     */
    default void stderrPath(final Provider<@NotNull RegularFile> fileProvider) {
        getStderrPath().set(fileProvider.map(f -> f.getAsFile().getAbsolutePath()));
    }

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
