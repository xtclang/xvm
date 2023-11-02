package org.xvm.plugin;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.file.DirectoryProperty;
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

import static org.xvm.plugin.Constants.XTC_MODULE_FILE_EXTENSION;

public abstract class ProjectDelegate<T, R> {
    private static final String DEFAULT_VERSION_CATALOG_NAME = "libs";
    private static final boolean LOG_ALL_LEVELS_TO_STDERR;

    static {
        final String logAll = System.getenv("XTC_LOG_STDERR");
        LOG_ALL_LEVELS_TO_STDERR = Boolean.parseBoolean(logAll) || Boolean.parseBoolean(System.getProperty("XTC_LOG_STDERR"));
    }

    protected final Project project;
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

    @SuppressWarnings("unused")
    ProjectDelegate(final Project project) {
        this(project, null);
    }

    ProjectDelegate(final Project project, final AdhocComponentWithVariants component) {
        this.project = project;
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

        // add a property to the existing environment.
        extra.set("logPrefix", prefix);
    }

    XtcBuildException buildException(final String msg) {
        return buildException(msg, null);
    }

    XtcBuildException buildException(final String msg, final Throwable cause) {
        if (LOG_ALL_LEVELS_TO_STDERR) {
            error(msg);
        }
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

    void log(final LogLevel level, final String str, Object... args) {
        final String msg = String.format(str.replace("{}", "%s"), args);
        if (LOG_ALL_LEVELS_TO_STDERR) {
            System.err.println(msg);
        }
        logger.log(level, msg);
    }

    void error(final String str, Object... args) {
        log(LogLevel.ERROR, str, args);
    }

    void warn(final String str, Object... args) {
        log(LogLevel.WARN, str, args);
    }

    void lifecycle(final String str, Object... args) {
        log(LogLevel.LIFECYCLE, str, args);
    }

    void info(final String str, Object... args) {
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

    protected MinimalExternalModuleDependency findVersionCatalogLib(final String name) {
        final var catalog = findVersionCatalog();
        if (catalog == null) {
            return null;
        }
        return catalog.findLibrary(name).map(Provider::get).orElse(null);
    }

    protected String findVersionCatalogVersion(final String name) {
        return findVersionCatalogVersion(name, Project.DEFAULT_VERSION);
    }

    // TODO Add the version catalog Gradle tree walker!
    protected String findVersionCatalogVersion(final String name, final String defaultVersion) {
        final var catalog = findVersionCatalog();
        if (catalog == null) {
            return defaultVersion;
        }
        final var version = catalog.findVersion(name);
        return version.map(VersionConstraint::getRequiredVersion).orElse(defaultVersion);
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

    protected final R apply() {
        return apply(null);
    }

    protected abstract R apply(T arg);
}
