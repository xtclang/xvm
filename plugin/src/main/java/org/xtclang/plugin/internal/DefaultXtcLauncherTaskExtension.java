package org.xtclang.plugin.internal;

import static org.xtclang.plugin.XtcPluginConstants.PLUGIN_BUILD_INFO_RESOURCE_PATH;
import static org.xtclang.plugin.XtcPluginConstants.PROPERTY_LAUNCHER_FORK;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

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
import org.xtclang.plugin.XtcPluginUtils;

public abstract class DefaultXtcLauncherTaskExtension implements XtcLauncherTaskExtension {
    private static final Logger logger = Logging.getLogger(DefaultXtcLauncherTaskExtension.class);
    private static final List<String> DEFAULT_JVM_ARGS = List.of("-ea");

    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract ProviderFactory getProviders();

    protected final List<String> defaultJvmArgs;
    protected final ConfigurableFileCollection modulePath;
    protected final Property<@NotNull Boolean> fork;
    protected final ListProperty<@NotNull String> jvmArgs;
    protected final Property<@NotNull Boolean> verbose;
    protected final Property<@NotNull Boolean> showVersion;
    protected final Property<@NotNull String> stdoutPath;
    protected final Property<@NotNull String> stderrPath;

    @Inject
    @SuppressWarnings("this-escape")
    protected DefaultXtcLauncherTaskExtension() {
        final ObjectFactory objects = getObjects();
        final ProviderFactory providers = getProviders();
        this.modulePath = objects.fileCollection();
        this.defaultJvmArgs = loadDefaultJvmArgs();
        logger.info("[plugin] Loaded default JVM args: {}", defaultJvmArgs);
        this.fork = objects.property(Boolean.class).convention(resolveForkProperty(providers));
        this.jvmArgs = objects.listProperty(String.class).convention(defaultJvmArgs);
        this.verbose = objects.property(Boolean.class).convention(false);
        this.showVersion = objects.property(Boolean.class).convention(false);
        this.stdoutPath = objects.property(String.class);
        this.stderrPath = objects.property(String.class);
    }

    // TODO: Sort public methods in alphabetical order for all these files, remove where just inheritance that has
    //  been added to the superclass already if any are left, and put public methods first.
    @Override
    public ConfigurableFileCollection getModulePath() {
        return modulePath;
    }

    @Override
    public Property<@NotNull Boolean> getFork() {
        return fork;
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
        jvmArgs(getObjects().listProperty(String.class).value(arg.map(Collections::singletonList)));
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

    /**
     * Resolves the fork property from Gradle project properties.
     * Checks for the property {@link org.xtclang.plugin.XtcPluginConstants#PROPERTY_LAUNCHER_FORK} which can be set:
     * - In gradle.properties file
     * - Via command line: -Porg.xtclang.plugin.launcher.fork=false
     * - Via system property: -Dorg.gradle.project.org.xtclang.plugin.launcher.fork=false
     *
     * @param providers The Gradle ProviderFactory for accessing properties
     * @return The fork value from property, or true (default) if not specified
     */
    private static boolean resolveForkProperty(final ProviderFactory providers) {
        final Provider<@NotNull String> propertyValue = providers.gradleProperty(PROPERTY_LAUNCHER_FORK);
        return !propertyValue.isPresent() || Boolean.parseBoolean(propertyValue.get());
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
}
