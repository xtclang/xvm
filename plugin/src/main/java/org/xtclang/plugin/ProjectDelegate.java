package org.xtclang.plugin;

import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.DirectoryProperty;
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

import static org.xtclang.plugin.XtcPluginConstants.XTC_PLUGIN_VERBOSE_PROPERTY;

public abstract class ProjectDelegate<T, R> {
    public static final boolean OVERRIDE_VERBOSE_LOGGING = "true".equalsIgnoreCase(System.getenv(XTC_PLUGIN_VERBOSE_PROPERTY));

    protected final Project project;
    protected final String projectName;
    protected final String prefix;
    protected final ObjectFactory objects;
    protected final Logger logger;
    protected final Gradle gradle;
    protected final StartParameter startParameter;
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
        // Even if we add tasks later, this refers to a task container, so it's fine to initialize it here, and it can be final
        this.tasks = project.getTasks();
        this.extensions = project.getExtensions();
        this.buildDir = layout.getBuildDirectory();
        this.extra = extensions.getByType(ExtraPropertiesExtension.class);
        this.versionCatalogExtension = extensions.findByType(VersionCatalogsExtension.class);
        this.component = component;
        this.pluginUrl = getClass().getProtectionDomain().getCodeSource().getLocation();

        // add a property to the existing environment, project.setProperty assumes the property exists already
        extra.set("logPrefix", prefix);
    }

    @SuppressWarnings("UnusedReturnValue")
    protected final R apply() {
        return apply(null);
    }

    public abstract R apply(T arg);

    public boolean hasVerboseLogging() {
        return hasVerboseLogging(project);
    }

    public static boolean hasVerboseLogging(final Project project) {
        return switch (getLogLevel(project)) {
            case DEBUG, INFO -> true;
            default -> OVERRIDE_VERBOSE_LOGGING;
        };
    }

    public final XtcBuildRuntimeException buildException(final String msg, final Object... args) {
        return buildException(null, msg, args);
    }

    public static XtcBuildRuntimeException buildException(final Logger logger, final String prefix, final String msg, final Object... args) {
        return buildException(logger, prefix, null, msg, args);
    }

    public final XtcBuildRuntimeException buildException(final Throwable t, final String msg, final Object... args) {
        return buildException(logger, prefix, t, msg, args);
    }

    public static XtcBuildRuntimeException buildException(final Logger logger, final String prefix, final Throwable t, final String msg, final Object... args) {
        logger.error(msg, t);
        return new XtcBuildRuntimeException(t, prefix + ": " + msg, args);
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

    public static LogLevel getLogLevel(final Project project) {
        return project.getGradle().getStartParameter().getLogLevel();
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
    public void considerNeverUpToDate(final Task task) {
        task.getOutputs().cacheIf(t -> false);
        task.getOutputs().upToDateWhen(t -> false);
        logger.warn("{} WARNING: '{}' is configured to always be treated as out of date, and will be run. Do not include this as a part of the normal build cycle!", prefix, task.getName());
    }

    protected static <E> E ensureExtension(final Project project, final String name, final Class<E> clazz) {
        final var exts = project.getExtensions();
        if (exts.findByType(clazz) == null) { // WARNING TODO TODO There can be several run and compile extensions (one per source set), if I understand conventions correctly. Or we cold just add the configuration inline to our source sets or have sections in the xtcXXX extensions or something.
            return exts.create(name, clazz, project);
        }
        return exts.getByType(clazz);
    }
}
