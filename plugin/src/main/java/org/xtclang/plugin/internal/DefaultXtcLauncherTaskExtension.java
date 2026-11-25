package org.xtclang.plugin.internal;

import java.util.Collections;
import java.util.List;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.XtcPluginConstants;
import org.xtclang.plugin.XtcPluginUtils;
import org.xtclang.plugin.launchers.ExecutionMode;

public abstract class DefaultXtcLauncherTaskExtension implements XtcLauncherTaskExtension {
    private static final Logger LOGGER = Logging.getLogger(DefaultXtcLauncherTaskExtension.class);

    protected final ObjectFactory objects;
    protected final ProviderFactory providers;
    protected final ConfigurableFileCollection modulePath;
    protected final Property<@NotNull ExecutionMode> executionMode;
    protected final ListProperty<@NotNull String> jvmArgs;
    protected final Property<@NotNull Boolean> enableAssertions;
    protected final Property<@NotNull Boolean> verbose;
    protected final Property<@NotNull Boolean> showVersion;
    protected final Property<@NotNull String> stdoutPath;
    protected final Property<@NotNull String> stderrPath;

    protected DefaultXtcLauncherTaskExtension(final ObjectFactory objects, final ProviderFactory providers) {
        final var executionModeProperty =
            providers.gradleProperty(XtcPluginConstants.PROPERTY_DEFAULT_EXECUTION_MODE);

        this.objects = objects;
        this.providers = providers;
        this.modulePath = objects.fileCollection();
        this.verbose = objects.property(Boolean.class).convention(false);
        this.showVersion = objects.property(Boolean.class).convention(false);
        this.stdoutPath = objects.property(String.class);
        this.stderrPath = objects.property(String.class);
        this.executionMode = objects.property(ExecutionMode.class).convention(
            executionModeProperty
                .map(DefaultXtcLauncherTaskExtension::parseExecutionMode)
                .orElse(XtcPluginConstants.DEFAULT_EXECUTION_MODE)
        );
        // only the build script's, empty until it gives some; the plugin's own options and -ea are not among them
        this.jvmArgs = objects.listProperty(String.class);
        this.enableAssertions = objects.property(Boolean.class).convention(XtcLauncherDefaults.enableAssertions());
        LOGGER.info("[plugin] Launcher JVM options: {}, assertions enabled by default: {}",
            XtcLauncherDefaults.jvmOptions(), XtcLauncherDefaults.enableAssertions());
        if (executionModeProperty.isPresent()) {
            LOGGER.info("[plugin] Default execution mode overridden by Gradle property '{}={}'",
                XtcPluginConstants.PROPERTY_DEFAULT_EXECUTION_MODE, executionModeProperty.get());
        }
    }

    @Override
    public ConfigurableFileCollection getModulePath() {
        return modulePath;
    }

    @Override
    public Property<@NotNull ExecutionMode> getExecutionMode() {
        return executionMode;
    }

    @Override
    public Property<@NotNull String> getStdoutPath() {
        return stdoutPath;
    }

    @Override
    public Property<@NotNull String> getStderrPath() {
        return stderrPath;
    }

    @Override
    public Property<@NotNull Boolean> getShowVersion() {
        return showVersion;
    }

    @Override
    public Property<@NotNull Boolean> getVerbose() {
        return verbose;
    }

    @Override
    public ListProperty<@NotNull String> getJvmArgs() {
        return jvmArgs;
    }

    @Override
    public Property<@NotNull Boolean> getEnableAssertions() {
        return enableAssertions;
    }

    @Override
    public void jvmArg(final Provider<? extends @NotNull String> arg) {
        jvmArgs(objects.listProperty(String.class).value(arg.map(Collections::singletonList)));
    }

    @Override
    public void jvmArgs(final String... args) {
        jvmArgs(XtcPluginUtils.argumentArrayToList(args));
    }

    @Override
    public void jvmArgs(final Iterable<? extends String> elements) {
        jvmArgs.addAll(elements);
    }

    @Override
    public void jvmArgs(final Provider<? extends @NotNull Iterable<? extends String>> provider) {
        jvmArgs.addAll(provider);
    }

    @Override
    public void setJvmArgs(final Iterable<? extends String> elements) {
        jvmArgs.set(elements);
    }

    @Override
    public void setJvmArgs(final Provider<? extends @NotNull Iterable<? extends String>> provider) {
        jvmArgs.set(provider);
    }

    private static ExecutionMode parseExecutionMode(final String rawValue) {
        final var value = rawValue.trim();
        try {
            return ExecutionMode.valueOf(value.toUpperCase());
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid value for Gradle property '" + XtcPluginConstants.PROPERTY_DEFAULT_EXECUTION_MODE
                    + "': '" + rawValue + "'. Expected one of: "
                    + List.of(ExecutionMode.values()),
                e
            );
        }
    }
}
