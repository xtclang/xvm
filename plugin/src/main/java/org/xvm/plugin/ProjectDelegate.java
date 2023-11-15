package org.xvm.plugin;

import org.gradle.api.GradleException;
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
import java.util.Arrays;
import java.util.List;

import static org.xvm.plugin.Constants.XTC_MODULE_FILE_EXTENSION;

public abstract class ProjectDelegate<T, R> {
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

        // add a property to the existing environment, project.setProperty assumes the property exists already
        extra.set("logPrefix", prefix);
    }

    @SuppressWarnings("UnusedReturnValue")
    protected final R apply() {
        return apply(null);
    }

    public abstract R apply(T arg);

    public XtcBuildException buildException(final String msg) {
        return buildException(msg, (Throwable)null);
    }

    public XtcBuildException buildException(final String msg, final Throwable cause) {
        if (LOG_ALL_LEVELS_TO_STDERR) {
            error(msg);
        }
        logToFiles(LogLevel.ERROR, msg);
        return XtcBuildException.buildException(logger, prefix + ": " + msg, cause);
    }

    public XtcBuildException buildException(final String msg, final Object... args) {
        final var template = msg.replace("{}", "#");
        final var list = Arrays.asList(args);
        final var sb = new StringBuilder();
        for (int i = 0, pos = 0; i < template.length(); i++) {
            final var c = msg.charAt(i);
            if (c == '#') {
                if (pos >= list.size()) {
                    throw new IllegalStateException("More ellipses than tokens in expansion.");
                }
                sb.append(list.get(pos++));
            } else {
                sb.append(c);
            }
        }
        return buildException(sb.toString());
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
        final String msg = String.format(str.replace("{}", "%s"), args);
        if (LOG_ALL_LEVELS_TO_STDERR) {
            System.err.println(msg);
        }
        logToFiles(level, msg);
        logger.log(level, msg);
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

    public void alwaysRerunTask(final Task task) {
        // Used to implement forceRebuild
        // To work around Gradle caches, we are also marking this task as always stale, refusing it to cache inputs -> outputs. Be aware that
        // this will totally remove most of the benefits of Gradle.
        task.getOutputs().cacheIf(t -> false);
        task.getOutputs().upToDateWhen(t -> false);
        warn("{} WARNING: Task '{}:{}' is configured to always be treated as out of date, and will be run. Do not include this as a part of the normal build cycle...", prefix, projectName, task.getName());
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
}
