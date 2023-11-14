package org.xvm.plugin;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.TaskContainer;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.xvm.plugin.Constants.XTC_MODULE_FILE_EXTENSION;

public abstract class ProjectDelegate<T, R> {
    private static final String DEFAULT_VERSION_CATALOG_NAME = "libs";

    private static final boolean LOG_ALL_LEVELS_TO_STDERR;
    private static final boolean LOG_ALL_LEVELS_TO_BUILD_FILE;

    static {
        LOG_ALL_LEVELS_TO_STDERR = queryProperty("XTC_LOG_STDERR", false); // or system property xtc.log.stderr = ...
        LOG_ALL_LEVELS_TO_BUILD_FILE = queryProperty("XTC_LOG_FILE", false); // or system property xtc.log.file = ...
    }

    protected final Project project;
    protected final String projectName;
    protected final String prefix;
    protected final ObjectFactory objects;
    protected final Logger logger;
    protected final Gradle gradle;
    protected final ConfigurationContainer configs;
    protected final AdhocComponentWithVariants component;
    protected final URL pluginUrl;
    protected final TaskContainer tasks;
    protected final ProjectLayout layout;
    protected final DirectoryProperty buildDir;
    protected final ExtraPropertiesExtension extra;
    protected final ExtensionContainer extensions;
    protected final VersionCatalogsExtension versionCatalogExtension;
    protected final List<File> logFiles;

    @SuppressWarnings("unused")
    ProjectDelegate(final Project project) {
        this(project, null);
    }

    ProjectDelegate(final Project project, final AdhocComponentWithVariants component) {
        this.project = project;
        this.projectName = project.getName();
        this.prefix = prefix();
        this.objects = project.getObjects();
        this.layout = project.getLayout();
        this.gradle = project.getGradle();
        this.configs = getProject().getConfigurations();
        this.buildDir = layout.getBuildDirectory();
        this.logger = project.getLogger();
        this.tasks = project.getTasks();
        this.extensions = project.getExtensions();
        this.extra = extensions.getByType(ExtraPropertiesExtension.class);
        this.versionCatalogExtension = extensions.findByType(VersionCatalogsExtension.class);
        this.component = component;
        this.pluginUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
        this.logFiles = new ArrayList<>();

        // add a property to the existing environment.
        extra.set("logPrefix", prefix);
        project.setProperty("logPrefix", prefix); // TODO Allowed?
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

    private Gradle rootGradle() {
        Gradle gradle = project.getGradle();
        while (gradle.getParent() != null) {
            gradle = gradle.getParent();
        }
        // TODO: Add Gradle.useLogger, with a project prefixed logger.
        return gradle;
    }

    private File logFileFull() {
        return new File(rootGradle().getRootProject().getLayout().getBuildDirectory().get().getAsFile(), "build-xdk.log");
    }

    private File logFile() {
        return new File(layout.getBuildDirectory().get().getAsFile(), "build-" + projectName + ".log");
    }

    private void logToFiles(final LogLevel level, final String msg) {
        if (LOG_ALL_LEVELS_TO_BUILD_FILE) {
            if (logFiles.isEmpty()) {
                logFiles.add(logFileFull());
                logFiles.add(logFile());
            }
            logFiles.forEach(log -> {
                try (final var out = new PrintWriter(new FileWriter(log, true), true)) {
                    final String hash = "0x" + Integer.toHexString(gradle.hashCode());
                    final long pid = ProcessHandle.current().pid();
                    out.println(hash + ' ' + pid + ' ' + level.name() + ": " + msg);
                } catch (final IOException e) {
                    throw new GradleException(prefix + " Failed to write log files to. " + logFile().getAbsolutePath() + " and/or " + logFileFull().getAbsolutePath(), e);
                }
            });
        }
    }

    XtcBuildException buildException(final String msg) {
        return buildException(msg, null);
    }

    XtcBuildException buildException(final String msg, final Throwable cause) {
        if (LOG_ALL_LEVELS_TO_STDERR) {
            error(msg);
        }
        logToFiles(LogLevel.ERROR, msg);
        return XtcBuildException.buildException(logger, prefix + ": " + msg, cause);
    }

    String prefix() {
        return prefix(project);
    }

    String prefix(final String id) {
        return prefix(project, id);
    }

    static String prefix(final Project project) {
        return '[' + project.getName() + ']';
    }

    static String prefix(final Project project, final String id) {
        return prefix(project) + " '" + id + '\'';
    }

    void log(final LogLevel level, final String str, final Object... args) {
        final String msg = String.format(str.replace("{}", "%s"), args);
        if (LOG_ALL_LEVELS_TO_STDERR) {
            System.err.println(msg);
        }
        logToFiles(level, msg);
        logger.log(level, msg);
    }

    void error(final String str, final Object... args) {
        log(LogLevel.ERROR, str, args);
    }

    void warn(final String str, final Object... args) {
        log(LogLevel.WARN, str, args);
    }

    void lifecycle(final String str, final Object... args) {
        log(LogLevel.LIFECYCLE, str, args);
    }

    void info(final String str, final Object... args) {
        log(LogLevel.INFO, str, args);
    }

    private VersionCatalog findVersionCatalog() {
        return findVersionCatalog(DEFAULT_VERSION_CATALOG_NAME);
    }

    private VersionCatalog findVersionCatalog(final String name) {
        if (versionCatalogExtension == null) {
            return null;
        }
        return versionCatalogExtension.find(name).orElse(null);
    }

    boolean isXtcBinary(final File file) {
        return isXtcBinary(file, true);
    }

    @SuppressWarnings("SameParameterValue")
    boolean isXtcBinary(final File file, final boolean checkMagic) {
        if (!file.exists() || !file.isFile() || !hasFileExtension(file, XTC_MODULE_FILE_EXTENSION)) {
            return false;
        }
        if (!checkMagic) {
            return true;
        }
        try (final var dis = new DataInputStream(new FileInputStream(file))) {
            final long magic = dis.readInt() & 0xffff_ffffL;
            if (magic != Constants.XTC_MAGIC) {
                error("{} File '{}' should have started with magic value 0x{} (read: 0x{})", prefix, file.getAbsolutePath(), Long.toHexString(Constants.XTC_MAGIC), Long.toHexString(magic));
            }
            return true;
        } catch (final IOException e) {
            error("{} Error parsing XTC_MAGIC: {}", prefix, e.getMessage());
            return false;
        }
    }

    protected Project getProject() {
        return project;
    }

    protected ObjectFactory getObjects() {
        return objects;
    }

    protected <E> E ensureExtension(final String name, final Class<E> clazz) {
        if (extensions.findByType(clazz) == null) {
            return extensions.create(name, clazz, project);
        }
        return extensions.getByType(clazz);
    }

    protected Logger getLogger() {
        return project.getLogger();
    }

    static boolean hasFileExtension(final File file, final String extension) {
        return getFileExtension(file).equalsIgnoreCase(extension);
    }

    static String getFileExtension(final File file) {
        final String name = file.getName();
        final int dot = name.lastIndexOf('.');
        return dot == -1 ? "" : name.substring(dot + 1);
    }

    static String capitalize(final String string) {
        return Character.toUpperCase(string.charAt(0)) + string.substring(1);
    }

    void alwaysRerunTask(final Task task) {
        // Used to implement forceRebuild
        // To work around Gradle caches, we are also marking this task as always stale, refusing it to cache inputs -> outputs. Be aware that
        // this will totally remove most of the benefits of Gradle.
        task.getOutputs().cacheIf(t -> false);
        task.getOutputs().upToDateWhen(t -> false);
        warn("{} WARNING: Task '{}:{}' is configured to always be treated as out of date, and will be run. Do not include this as a part of the normal build cycle...", prefix, projectName, task.getName());
    }

    @SuppressWarnings("UnusedReturnValue")
    protected final R apply() {
        return apply(null);
    }

    protected abstract R apply(T arg);
}
