package org.xtclang.plugin;

import static org.xtclang.plugin.XtcPluginConstants.PLUGIN_BUILD_INFO_FILENAME;
import static org.xtclang.plugin.XtcPluginConstants.PLUGIN_BUILD_INFO_RESOURCE_PATH;
import static org.xtclang.plugin.XtcPluginConstants.PROPERTY_VERBOSE_LOGGING_OVERRIDE;

import java.net.URL;
import java.util.Properties;

import org.gradle.StartParameter;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ConfigurationContainer;
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

    // Cached XDK version and semantic version read from plugin-build-info.properties (read once at construction)
    private final String xdkVersion;
    private final String xdkSemanticVersion;

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
        this.component = component;
        this.pluginUrl = getClass().getProtectionDomain().getCodeSource().getLocation();

        // Read XDK version once at construction time and compute semantic version
        this.xdkVersion = readXdkVersionFromBuildInfo();
        this.xdkSemanticVersion = "org.xtclang:" + projectName + ':' + xdkVersion;
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

    /**
     * Get the cached XDK version read from plugin-build-info.properties.
     * This is read once at construction time and cached for the lifetime of the delegate.
     *
     * @return the XDK version
     */
    public String getXdkVersion() {
        return xdkVersion;
    }

    /**
     * Get the cached semantic version for this project.
     * Format: org.xtclang:&lt;projectName&gt;:&lt;xdkVersion&gt;
     * This is computed once at construction time and cached for the lifetime of the delegate.
     *
     * @return the semantic version string
     */
    public String getXdkSemanticVersion() {
        return xdkSemanticVersion;
    }

    /**
     * Read the XDK version from the plugin's build-info.properties resource.
     * This is called once at construction time - use getXdkVersion() to access the cached value.
     *
     * @return the XDK version
     * @throws GradleException if the version cannot be read
     */
    private String readXdkVersionFromBuildInfo() {
        final var version = readXdkVersion();
        logger.info("[plugin] Read XDK version from {}: {}", PLUGIN_BUILD_INFO_FILENAME, version);
        return version;
    }

    /**
     * Static utility to read the XDK version from the plugin's build-info.properties.
     * This reads directly from the classpath resource without needing a ProjectDelegate instance.
     *
     * @return the XDK version
     * @throws GradleException if the version cannot be read
     */
    public static String readXdkVersion() {
        try (final var resourceStream = ProjectDelegate.class.getResourceAsStream(PLUGIN_BUILD_INFO_RESOURCE_PATH)) {
            if (resourceStream == null) {
                throw new IllegalStateException("Cannot find " + PLUGIN_BUILD_INFO_FILENAME + " in plugin JAR");
            }

            final var props = new Properties();
            props.load(resourceStream);
            final var version = props.getProperty("xdk.version");

            if (version == null || version.isBlank()) {
                throw new IllegalStateException("xdk.version not found in " + PLUGIN_BUILD_INFO_FILENAME);
            }

            return version;
        } catch (final Exception e) {
            throw new GradleException("[plugin] FATAL: Plugin build is broken - cannot read XDK version: " + e.getMessage(), e);
        }
    }

    /**
     * Get the semantic version string for a project using the XDK version from build-info.properties.
     * Format: org.xtclang:&lt;projectName&gt;:&lt;xdkVersion&gt;
     *
     * @param projectName the name of the project
     * @return the semantic version string
     * @throws GradleException if the XDK version cannot be read
     */
    public static String getSemanticVersion(final String projectName) {
        return "org.xtclang:" + projectName + ':' + readXdkVersion();
    }

    /**
     * Static utility to read the JDK version from the plugin's build-info.properties.
     * This reads directly from the classpath resource without needing a ProjectDelegate instance.
     *
     * @return the JDK version as an integer
     * @throws GradleException if the version cannot be read
     */
    public static int readJdkVersion() {
        try (final var resourceStream = ProjectDelegate.class.getResourceAsStream(PLUGIN_BUILD_INFO_RESOURCE_PATH)) {
            if (resourceStream == null) {
                throw new IllegalStateException("Cannot find " + PLUGIN_BUILD_INFO_FILENAME + " in plugin JAR");
            }

            final var props = new Properties();
            props.load(resourceStream);
            final var version = props.getProperty("jdk.version");

            if (version == null || version.isBlank()) {
                throw new IllegalStateException("jdk.version not found in " + PLUGIN_BUILD_INFO_FILENAME);
            }

            return Integer.parseInt(version);
        } catch (final Exception e) {
            throw new GradleException("[plugin] FATAL: Plugin build is broken - cannot read JDK version: " + e.getMessage(), e);
        }
    }
}
