package org.xtclang.plugin;

import static org.xtclang.plugin.XtcPluginConstants.PROPERTY_VERBOSE_LOGGING_OVERRIDE;

import java.net.URL;

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

public abstract class ProjectDelegate<T, R> {
    protected final Project project;
    protected final String projectName;
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
    protected ProjectDelegate(final Project project, final AdhocComponentWithVariants component) {
        this.project = project;
        this.projectName = project.getName();
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
    }

    @SuppressWarnings("UnusedReturnValue")
    protected final R apply() {
        return apply(null);
    }

    public abstract R apply(T arg);

    public boolean hasVerboseLogging() {
        return hasVerboseLogging(project);
    }

    /**
     * To display "extra important" info messages on the lifecycle level, we can set the xtcPluginOverrideVerboseLogging property,
     * or its System env equivalent, ORG_GRADLE_PROJECT_xtcPluginOverrideVerboseLogging, to true. "Extra important" means things
     * like the JavaExec command lines executed by the plugin, and other information that we might frequently cut and paste for
     * debugging.
     * <p>
     * @param project project for which to check if "extra important" logging should be enforced.
     * @return true if "extra important" logging should always be displayed at lifecycle level, false otherwise
     */
    public static boolean hasVerboseLogging(final Project project) {
        final var overrideVerboseLogging = Boolean.parseBoolean(String.valueOf(project.findProperty(PROPERTY_VERBOSE_LOGGING_OVERRIDE)));
        return switch (getLogLevel(project)) {
        case DEBUG, INFO -> true;
        default -> overrideVerboseLogging;
        };
    }

    @SuppressWarnings("unused")
    public boolean showStackTraces() {
        return startParameter.getShowStacktrace() != ShowStacktrace.INTERNAL_EXCEPTIONS;
    }

    public ObjectFactory getObjects() {
        return objects;
    }

    public Logger getLogger() {
        return logger;
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
     * Can be used to implement, behaviors that require a task to run fresh every time.
     * Note that it's easier to just flag a task implementation as @NonCacheable. This is intended for
     * unit tested, extended existing tasks and finer granularity levels of dependencies.
     * The implementation forbids the task to cache outputs, and it will never be reported as
     * up to date. Be aware that this totally removes most of the benefits of Gradle.
     * <p>
     * From the user side, it makes more sense to do --rerun-tasks when building the project.
     *
     * @param task Task to flag as perpetually not up to date.
     */
    @SuppressWarnings("unused")
    public void considerNeverUpToDate(final Task task) {
        task.getOutputs().cacheIf(t -> false);
        task.getOutputs().upToDateWhen(t -> false);
        logger.warn("[plugin] WARNING: '{}' is configured to always be treated as out of date, and will be run.", task.getName());
    }

    protected static <E> E ensureExtension(final Project project, final String name, final Class<E> clazz) {
        final var exts = project.getExtensions();
        if (exts.findByType(clazz) == null) {
            return exts.create(name, clazz, project);
        }
        return exts.getByType(clazz);
    }
}
