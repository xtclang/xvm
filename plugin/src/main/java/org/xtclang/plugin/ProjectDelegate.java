package org.xtclang.plugin;

import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.TaskContainer;

import java.net.URL;
import java.util.Arrays;

import static org.xtclang.plugin.XtcPluginConstants.XTC_PLUGIN_VERBOSE_PROPERTY;

public abstract class ProjectDelegate<T, R> {
    protected final Project project;
    protected final String projectName;
    protected final String prefix;
    protected final ObjectFactory objects;
    protected final Logger logger;
    protected final Gradle gradle;
    protected final StartParameter startParameter;
    protected final boolean overrideVerboseLogging;
    protected final ConfigurationContainer configs;
    protected final AdhocComponentWithVariants component;
    protected final URL pluginUrl;
    protected final TaskContainer tasks;
    protected final ProjectLayout layout;
    protected final DirectoryProperty buildDir;
    protected final ExtraPropertiesExtension extra;
    protected final ExtensionContainer extensions;
    protected final VersionCatalogsExtension versionCatalogExtension;

    @SuppressWarnings("unused")
    protected ProjectDelegate(final Project project) {
        this(project, null);
    }

    protected ProjectDelegate(final Project project, final String taskName) {
        this(project, taskName, null);
    }

    protected ProjectDelegate(final Project project, final String taskName, final AdhocComponentWithVariants component) {
        this.project = project;
        this.projectName = project.getName();
        this.prefix = prefix(taskName);
        this.objects = project.getObjects();
        this.layout = project.getLayout();
        this.gradle = project.getGradle();
        this.startParameter = gradle.getStartParameter();
        this.configs = project.getConfigurations();
        this.logger = project.getLogger();
        this.tasks = project.getTasks();
        this.extensions = project.getExtensions();
        this.buildDir = layout.getBuildDirectory();
        this.extra = extensions.getByType(ExtraPropertiesExtension.class);
        this.versionCatalogExtension = extensions.findByType(VersionCatalogsExtension.class);
        this.component = component;
        this.pluginUrl = getClass().getProtectionDomain().getCodeSource().getLocation();

        // add a property to the existing environment, project.setProperty assumes the property exists already
        extra.set("logPrefix", prefix);

        // Used to print only key messages with an "always" semantic. Used to quickly switch on and off,
        // or persist in the shell, a setting that is used for stuff like always printing launcher command
        // lines, regardless of log level, but not doing it if the override is turned off (default).
        this.overrideVerboseLogging = "true".equalsIgnoreCase(System.getenv(XTC_PLUGIN_VERBOSE_PROPERTY));
        if (overrideVerboseLogging) {
            logger.info("{} ORG_XTCLANG_PLUGIN_VERBOSE=true; the XTC Plugin may log important 'info' level events at 'lifecycle' level instead.", prefix);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    protected final R apply() {
        return apply(null);
    }

    public abstract R apply(T arg);

    public final XtcBuildRuntimeException buildException(final String msg, final Object... args) {
        return buildException(null, msg, args);
    }

    public final XtcBuildRuntimeException buildException(final Throwable t, final String msg, final Object... args) {
        logger.error(msg, t);
        return new XtcBuildRuntimeException(t, prefix + ": " + msg, args);
    }

    /**
     * We count everything with the log level "info" or finer as verbose logging.
     *
     * @return True of we are running with verbose logging enabled, false otherwise.
     */
    public boolean hasVerboseLogging() {
        return switch (getLogLevel()) {
            case DEBUG, INFO -> true;
            default -> overrideVerboseLogging;
        };
    }

    @SuppressWarnings("unused")
    public boolean showStackTraces() {
        return startParameter.getShowStacktrace() != ShowStacktrace.INTERNAL_EXCEPTIONS;
    }

    public final String prefix() {
        return prefix(project);
    }

    public final String prefix(final Task task) {
        return prefix(project, task);
    }

    public final String prefix(final String taskName) {
        return prefix(projectName, taskName);
    }

    public static String prefix(final Project project) {
        return prefix(project, (String)null);
    }

    public static String prefix(final Project project, final Task task) {
        return prefix(project.getName(), task == null ? null : task.getName());
    }

    public static String prefix(final Project project, final String taskName) {
        return prefix(project.getName(), taskName);
    }

    public static String prefix(final String projectName, final String taskName) {
        final var taskString = taskName == null ? "" : (':' + taskName);
        return '[' + java.util.Objects.requireNonNull(projectName) + taskString + ']';
    }

    public Project getProject() {
        return project;
    }

    public String getProjectName() {
        return projectName;
    }

    public ObjectFactory getObjects() {
        return objects;
    }

    public Logger getLogger() {
        return project.getLogger();
    }

    @SuppressWarnings("unused")
    public LogLevel getLogLevel() {
        return startParameter.getLogLevel();
    }

    /**
     * Flag task as always needing to be re-run. Cached state will be ignored.
     * <p>
     * Can be used to implement, e.g., forceRebuild, and other behaviors that require a task to run fresh every
     * time. that absolutely need to be rerun every time. Note that it's easier to just flag a task implementation
     * as @NonCacheable. This is intended for unit tested, extended existing tasks and finer granularity levels
     * of dependencies. The implementation forbids the task to cache outputs, and it will never be reported as
     * up to date. Be aware that this totally removes most of the benefits of Gradle.
     *
     * @param task Task to flag as perpetually not up to date.
     */
    public void alwaysRerunTask(final Task task) {
        task.getOutputs().cacheIf(t -> false);
        task.getOutputs().upToDateWhen(t -> false);
        logger.warn("{} WARNING: '{}' is configured to always be treated as out of date, and will be run. Do not include this as a part of the normal build cycle!", prefix, task.getName());
    }

    public FileCollection filesFrom(final String... configNames) {
        return filesFrom(false, configNames);
    }

    public FileCollection filesFrom(final boolean shouldBeResolved, final String... configNames) {
        logger.info("{} Resolving filesFrom config: {}", prefix, Arrays.asList(configNames));
        FileCollection fc = objects.fileCollection();
        for (final var name : configNames) {
            final Configuration config = configs.getByName(name);
            if (shouldBeResolved && config.getState() != Configuration.State.RESOLVED) {
                throw buildException("Configuration '{}' is not resolved, which is a requirement from the task execution phase.", name);
            }
            final var files = project.files(config);
            logger.info("{} Scanning file collection: filesFrom: {} {}, files: {}", prefix, name, config.getState(), files.getFiles());
            fc = fc.plus(files);
        }
        fc.getAsFileTree().forEach(it -> logger.info("{}    Resolved fileTree '{}'", prefix, it.getAbsolutePath()));
        return fc;
    }

    protected <E> E ensureExtension(final String name, final Class<E> clazz) {
        if (extensions.findByType(clazz) == null) {
            return extensions.create(name, clazz, project);
        }
        return extensions.getByType(clazz);
    }

    @SuppressWarnings("unused")
    private static boolean queryProperty(final String envKey, final boolean defaultValue) {
        final var envValue = System.getenv(envKey);
        final var propKey = envKey.replace("_", ".").toLowerCase();
        final var propValue = System.getProperty(propKey);
        if (envValue == null && propValue == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(envKey) || Boolean.parseBoolean(propKey);
    }
}
