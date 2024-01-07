package org.xtclang.plugin;

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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskContainer;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;

import static org.xtclang.plugin.XtcPluginConstants.XTC_MAGIC;
import static org.xtclang.plugin.XtcPluginConstants.XTC_MODULE_FILE_EXTENSION;
import static org.xtclang.plugin.XtcPluginConstants.XTC_PLUGIN_VERBOSE_PROPERTY;

public abstract class ProjectDelegate<T, R> {
    protected final Project project;
    protected final String projectName;
    protected final String prefix;
    protected final ObjectFactory objects;
    protected final Logger logger;
    protected final Gradle gradle;
    protected final LogLevel logLevel;
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

    protected ProjectDelegate(final Project project, final AdhocComponentWithVariants component) {
        this.project = project;
        this.projectName = project.getName();
        this.prefix = prefix();
        this.objects = project.getObjects();
        this.layout = project.getLayout();
        this.gradle = project.getGradle();
        this.logLevel = gradle.getStartParameter().getLogLevel();
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
        this.overrideVerboseLogging = "true".equals(System.getenv(XTC_PLUGIN_VERBOSE_PROPERTY));
        if (overrideVerboseLogging) {
            logger.info("{} XTC_PLUGIN_VERBOSE=true; The XTC Plugin may log important 'info' level events at 'lifecycle' level instead.", prefix);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    protected final R apply() {
        return apply(null);
    }

    public abstract R apply(T arg);

    public XtcBuildException buildException(final String msg) {
        return buildException(null, msg);
    }

    public XtcBuildException buildException(final Throwable cause, final String msg) {
        return XtcBuildException.buildException(logger, prefix + ": " + msg, cause);
    }

    public XtcBuildException buildException(final String msg, final Object... args) {
        return buildException(null, msg, args);
    }

    public XtcBuildException buildException(final Throwable cause, final String msg, final Object... args) {
        final var template = msg.replace("{}", "#");
        final var list = Arrays.asList(args);
        final var sb = new StringBuilder();
        for (int i = 0, pos = 0; i < template.length(); i++) {
            final var c = template.charAt(i);
            if (c == '#') {
                if (pos >= list.size()) {
                    throw new IllegalArgumentException("More ellipses than tokens in expansion.");
                }
                sb.append(list.get(pos++));
            } else {
                sb.append(c);
            }
        }
        return buildException(cause, sb.toString());
    }

    /**
     * We count everything with the log level "info" or finer as verbose logging.
     *
     * @return True of we are running with verbose logging enabled, false otherwise.
     */
    public boolean hasVerboseLogging() {
        return switch (logLevel) {
            case DEBUG, INFO -> true;
            default -> overrideVerboseLogging;
        };
    }

    @SuppressWarnings("unchecked")
    public Provider<String> stringProvider(final Object object) {
        return object instanceof Provider<?> ? (Provider<String>)object : project.provider(() -> String.valueOf(object));
    }

    @SuppressWarnings("unchecked")
    public static Provider<String> stringProvider(final Project project, final Object object) {
        return object instanceof Provider<?> ? (Provider<String>)object : project.provider(() -> String.valueOf(object));
    }

    public static String provideString(final Object object) {
       return String.valueOf(((Provider<?>)object).get());
    }

    public final String prefix() {
        return prefix(project);
    }

    public final String prefix(final String id) {
        return prefix(project, id);
    }

    public static String prefix(final Project project) {
        return '[' + project.getName() + ']';
    }

    public static String prefix(final Project project, final String id) {
        return prefix(project) + " '" + id + '\'';
    }

    public void log(final LogLevel level, final String str, final Object... args) {
        logger.log(level, String.format(str.replace("{}", "%s"), args));
    }

    public void error(final String str, final Object... args) {
        log(LogLevel.ERROR, str, args);
    }

    public void warn(final String str, final Object... args) {
        log(LogLevel.WARN, str, args);
    }

    public void lifecycle(final String str, final Object... args) {
        log(LogLevel.LIFECYCLE, str, args);
    }

    public void info(final String str, final Object... args) {
        log(LogLevel.INFO, str, args);
    }

    public boolean isXtcBinary(final File file) {
        return isXtcBinary(file, true);
    }

    @SuppressWarnings("SameParameterValue")
    public boolean isXtcBinary(final File file, final boolean checkMagic) {
        if (!file.exists() || !file.isFile() || !hasFileExtension(file, XTC_MODULE_FILE_EXTENSION)) {
            return false;
        }
        if (!checkMagic) {
            return true;
        }
        try (final var in = new DataInputStream(new FileInputStream(file))) {
            final long magic = in.readInt() & 0xffff_ffffL;
            if (magic != XTC_MAGIC) {
                error("{} File '{}' should have started with magic value 0x{} (read: 0x{})", prefix, file.getAbsolutePath(), Long.toHexString(XTC_MAGIC), Long.toHexString(magic));
            }
            return true;
        } catch (final IOException e) {
            error("{} Error parsing XTC_MAGIC: {}", prefix, e.getMessage());
            return false;
        }
    }

    public Project getProject() {
        return project;
    }

    public ConfigurationContainer getConfigs() {
        return configs;
    }

    public ObjectFactory getObjects() {
        return objects;
    }

    public Logger getLogger() {
        return project.getLogger();
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public static boolean hasFileExtension(final File file, final String extension) {
        return getFileExtension(file).equalsIgnoreCase(extension);
    }

    public static String getFileExtension(final File file) {
        final String name = file.getName();
        final int dot = name.lastIndexOf('.');
        return dot == -1 ? "" : name.substring(dot + 1);
    }

    public static String capitalize(final String string) {
        return Character.toUpperCase(string.charAt(0)) + string.substring(1);
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
        warn("{} WARNING: Task '{}:{}' is configured to always be treated as out of date, and will be run. Do not include this as a part of the normal build cycle...", prefix, projectName, task.getName());
    }

    public FileCollection filesFrom(final String... configNames) {
        return filesFrom(false, configNames);
    }

    public FileCollection filesFrom(final boolean shouldBeResolved, final String... configNames) {
        info("{} Resolving filesFrom config: {}", prefix, Arrays.asList(configNames));
        FileCollection fc = objects.fileCollection();
        for (final var name : configNames) {
            final Configuration config = configs.getByName(name);
            if (shouldBeResolved && config.getState() != Configuration.State.RESOLVED) {
                throw buildException("Configuration '" + name + "' is not resolved; which is a requirement from this task execution phase.");
            }
            final var files = project.files(config);
            info("{} Scanning file collection: filesFrom: {} {}, files: {}", prefix, name, config.getState(), files.getFiles());
            fc = fc.plus(files);
        }
        fc.getAsFileTree().forEach(it -> info("{}    Resolved fileTree '{}'", prefix, it.getAbsolutePath()));
        return fc;
    }

    protected <E> E ensureExtension(final String name, final Class<E> clazz) {
        if (extensions.findByType(clazz) == null) {
            return extensions.create(name, clazz, project);
        }
        return extensions.getByType(clazz);
    }

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
