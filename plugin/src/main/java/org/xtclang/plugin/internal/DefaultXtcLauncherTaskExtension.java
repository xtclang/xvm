package org.xtclang.plugin.internal;

import static org.xtclang.plugin.XtcPluginConstants.PLUGIN_BUILD_INFO_RESOURCE_PATH;

import java.io.InputStream;
import java.io.OutputStream;

import java.util.Collections;
import java.util.List;

import static org.xtclang.plugin.XtcPluginConstants.DEFAULT_DEBUG_PORT;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.XtcPluginUtils;

public abstract class DefaultXtcLauncherTaskExtension implements XtcLauncherTaskExtension {
    private static final List<String> DEFAULT_JVM_ARGS = List.of("-ea");

    // Project field removed to avoid configuration cache serialization issues
    protected final ObjectFactory objects;
    protected final Logger logger;
    protected final List<String> defaultJvmArgs;

    protected final ListProperty<@NotNull String> jvmArgs;
    protected final Property<@NotNull Boolean> debug;
    protected final Property<@NotNull Integer> debugPort;
    protected final Property<@NotNull Boolean> debugSuspend;
    protected final Property<@NotNull Boolean> verbose;
    protected final Property<@NotNull Boolean> fork;
    protected final Property<@NotNull Boolean> showVersion;
    protected final Property<@NotNull Boolean> useNativeLauncher;
    protected final Property<@NotNull InputStream> stdin;
    protected final Property<@NotNull OutputStream> stdout;
    protected final Property<@NotNull OutputStream> stderr;

    protected DefaultXtcLauncherTaskExtension(final Project project) {
        this.objects = project.getObjects();
        this.logger = project.getLogger();

        final var env = System.getenv();

        // TODO: Consider replacing the debug configuration with the debug flags inherited directly from its JavaExec extension
        //   DSL, or at least reimplementing them so that they look the same for different kinds of launchers.
        this.debug = objects.property(Boolean.class).convention(Boolean.parseBoolean(env.getOrDefault("XTC_DEBUG", "false")));
        this.debugPort = objects.property(Integer.class).convention(Integer.parseInt(env.getOrDefault("XTC_DEBUG_PORT", String.valueOf(DEFAULT_DEBUG_PORT))));
        this.debugSuspend = objects.property(Boolean.class).convention(Boolean.parseBoolean(env.getOrDefault("XTC_DEBUG_SUSPEND", "true")));

        // Use default JVM args from properties file generated at plugin build time
        this.defaultJvmArgs = loadDefaultJvmArgs();
        logger.info("[plugin] Loaded default JVM args: {}", defaultJvmArgs);
        this.jvmArgs = objects.listProperty(String.class).convention(defaultJvmArgs);
        this.verbose = objects.property(Boolean.class).convention(false);
        this.fork = objects.property(Boolean.class).convention(true);
        this.showVersion = objects.property(Boolean.class).convention(false);
        this.useNativeLauncher = objects.property(Boolean.class).convention(false);
        this.stdin = objects.property(InputStream.class);
        this.stdout = objects.property(OutputStream.class);
        this.stderr = objects.property(OutputStream.class);
    }

    // TODO: Sort public methods in alphabetical order for all these files, remove where just inheritance that has
    //  been added to the superclass already if any are left, and put public methods first.
    @Override
    public Property<@NotNull InputStream> getStdin() {
        return stdin;
    }

    @Override
    public Property<@NotNull OutputStream> getStdout() {
        return stdout;
    }

    @Override
    public Property<@NotNull OutputStream> getStderr() {
        return stderr;
    }

    @Override
    public Property<@NotNull Boolean> getFork() {
        return fork;
    }

    @Override
    public Property<@NotNull Boolean> getShowVersion() {
        return showVersion;
    }

    @Override
    public Property<@NotNull Boolean> getUseNativeLauncher() {
        return useNativeLauncher;
    }

    @Override
    public Property<@NotNull Boolean> getVerbose() {
        return verbose;
    }

    @Override
    public Property<@NotNull Boolean> getDebug() {
        return debug;
    }

    @Override
    public Property<@NotNull Integer> getDebugPort() {
        return debugPort;
    }

    @Override
    public Property<@NotNull Boolean> getDebugSuspend() {
        return debugSuspend;
    }

    @Override
    public ListProperty<@NotNull String> getJvmArgs() {
        return jvmArgs;
    }

    @Override
    public void jvmArg(final Provider<? extends @NotNull String> arg) {
        // Use objects factory instead of Project to create provider
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
        final var combinedArgs = new java.util.ArrayList<>(defaultJvmArgs);
        elements.forEach(combinedArgs::add);
        jvmArgs.set(combinedArgs);
    }

    @Override
    public void setJvmArgs(final Provider<? extends @NotNull Iterable<? extends String>> provider) {
        // Always preserve default JVM args and add user args
        jvmArgs.set(provider.map(userArgs -> {
            final var combinedArgs = new java.util.ArrayList<>(defaultJvmArgs);
            userArgs.forEach(combinedArgs::add);
            return combinedArgs;
        }));
    }

    private static List<String> loadDefaultJvmArgs() {
        try (final var inputStream = DefaultXtcLauncherTaskExtension.class.getResourceAsStream(PLUGIN_BUILD_INFO_RESOURCE_PATH)) {
            final var props = new java.util.Properties();
            props.load(inputStream);
            return List.of(props.getProperty("defaultJvmArgs").split(","));
        } catch (final Exception e) {
            // Log warning and use fallback
            return DEFAULT_JVM_ARGS;
        }
    }

    public static boolean areJvmArgsModified(final List<String> jvmArgs) {
        return !loadDefaultJvmArgs().equals(jvmArgs);
    }
}
