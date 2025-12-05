package org.xtclang.plugin.internal;

import static org.xtclang.plugin.XtcPluginConstants.PLUGIN_BUILD_INFO_RESOURCE_PATH;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

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
    private static final List<String> DEFAULT_JVM_ARGS = List.of("-ea");

    protected final ObjectFactory objects;
    protected final ProviderFactory providers;
    protected final List<String> defaultJvmArgs;
    protected final ConfigurableFileCollection modulePath;
    protected final Property<@NotNull ExecutionMode> executionMode;
    protected final ListProperty<@NotNull String> jvmArgs;
    protected final Property<@NotNull Boolean> verbose;
    protected final Property<@NotNull Boolean> showVersion;
    protected final Property<@NotNull String> stdoutPath;
    protected final Property<@NotNull String> stderrPath;

    protected DefaultXtcLauncherTaskExtension(final ObjectFactory objects, final ProviderFactory providers) {
        this.objects = objects;
        this.providers = providers;
        this.modulePath = objects.fileCollection();
        this.verbose = objects.property(Boolean.class).convention(false);
        this.showVersion = objects.property(Boolean.class).convention(false);
        this.stdoutPath = objects.property(String.class);
        this.stderrPath = objects.property(String.class);
        this.executionMode = objects.property(ExecutionMode.class).convention(XtcPluginConstants.DEFAULT_EXECUTION_MODE);
        this.defaultJvmArgs = loadDefaultJvmArgs();
        this.jvmArgs = objects.listProperty(String.class).convention(defaultJvmArgs);
        LOGGER.info("[plugin] Loaded default JVM args: {}", defaultJvmArgs);
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
        // Always preserve default JVM args and add user args
        final var combinedArgs = new ArrayList<>(defaultJvmArgs);
        elements.forEach(combinedArgs::add);
        jvmArgs.set(combinedArgs);
    }

    @Override
    public void setJvmArgs(final Provider<? extends @NotNull Iterable<? extends String>> provider) {
        // Always preserve default JVM args and add user args
        jvmArgs.set(provider.map(userArgs -> {
            final var combinedArgs = new ArrayList<>(defaultJvmArgs);
            userArgs.forEach(combinedArgs::add);
            return combinedArgs;
        }));
    }

    private static List<String> loadDefaultJvmArgs() {
        try (final var inputStream = DefaultXtcLauncherTaskExtension.class.getResourceAsStream(PLUGIN_BUILD_INFO_RESOURCE_PATH)) {
            final var props = new Properties();
            props.load(inputStream);
            return List.of(props.getProperty("defaultJvmArgs").split(","));
        } catch (final Exception e) {
            // Log warning and use fallback
            return DEFAULT_JVM_ARGS;
        }
    }
}