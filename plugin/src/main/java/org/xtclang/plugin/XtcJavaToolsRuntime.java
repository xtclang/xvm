package org.xtclang.plugin;

import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcPluginUtils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipFile;

import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_JAVATOOLS_INCOMING;
import static org.xtclang.plugin.XtcPluginConstants.XDK_JAVATOOLS_NAME_JAR;
import static org.xtclang.plugin.XtcPluginConstants.XDK_JAVATOOLS_NAME_MANIFEST;
import static org.xtclang.plugin.XtcPluginUtils.FileUtils.formatJarMetadata;
import static org.xtclang.plugin.XtcPluginUtils.FileUtils.isValidJavaToolsArtifact;
import static org.xtclang.plugin.XtcPluginUtils.FileUtils.readXdkVersionFromJar;
import static org.xtclang.plugin.XtcPluginUtils.failure;

/**
 * Runtime utility for resolving launcher runtime artifacts throughout the plugin.
 *
 * <p>This class resolves the launcher runtime in two different scenarios:
 *
 * <p><b>Scenario 1: XDK Development (building XDK itself)</b>
 * <ul>
 *   <li>Plugin has compileOnly + runtimeOnly dependency on javatools</li>
 *   <li>Gradle automatically supplies launcher artifacts from the composite build</li>
 *   <li>resolveRuntime() finds the launcher runtime from the composite build</li>
 * </ul>
 *
 *
 * <ul>
 *   <li>Plugin has compileOnly dependency on javatools (type info only)</li>
 *   <li>User project has XDK distribution as dependency (contains javatools.jar)</li>
 *   <li>resolveRuntime() finds the launcher runtime from extracted XDK contents</li>
 * </ul>
 *
 * <p><b>Why Two Methods:</b>
 * <ul>
 *   <li>resolveRuntime() - Finds the full launcher runtime classpath</li>
 *   <li>resolveJavaTools() - Finds javatools.jar file path (needed for forked processes)</li>
 * </ul>
 */
public final class XtcJavaToolsRuntime {
    private XtcJavaToolsRuntime() {
        // Utility class
    }

    public static XtcLauncherRuntime resolveRuntime(
            @NotNull final Provider<@NotNull String> projectVersion,
            @NotNull final Provider<@NotNull FileCollection> javaToolsConfig,
            @NotNull final Provider<@NotNull FileTree> xdkFileTree,
            @NotNull final Logger logger) {

        final var artifactVersion = projectVersion.get();
        final var configRuntime = resolveRuntimeFromFiles(
            "xdkJavaTools configuration",
            javaToolsConfig.get().getFiles().stream().filter(File::isFile).toList(),
            artifactVersion
        );
        final var xdkRuntime = resolveRuntimeFromFiles(
            "extracted XDK contents",
            xdkFileTree.get().getFiles().stream().filter(File::isFile).toList(),
            artifactVersion
        );

        if (configRuntime == null && xdkRuntime == null) {
            throw failure("ERROR: Failed to resolve '{}' runtime from any configuration or dependency. Ensure the XDK dependency is configured correctly.", XDK_JAVATOOLS_NAME_JAR);
        }

        if (configRuntime != null && xdkRuntime != null) {
            final var sameLauncherVersion = readXdkVersionFromJar(configRuntime.launcherJar()).equals(readXdkVersionFromJar(xdkRuntime.launcherJar()));
            if (!sameLauncherVersion || !sameClasspath(configRuntime.classpath(), xdkRuntime.classpath())) {
                logger.warn("[plugin] Resolved different launcher runtimes from '{}' and '{}'; preferring '{}'",
                    configRuntime.source(), xdkRuntime.source(), configRuntime.source());
            }
            logResolvedRuntime(logger, configRuntime);
            return configRuntime;
        }

        final var runtime = configRuntime != null ? configRuntime : xdkRuntime;
        logResolvedRuntime(logger, runtime);
        return runtime;
    }

    /**
     * Resolves the javatools.jar file from the XDK build or distribution.
     *
     * <p>This method handles both scenarios:
     * <ul>
     *   <li>XDK development: Resolves from javatools project in composite build</li>
     *   <li>Third-party use: Resolves from extracted XDK distribution zip</li>
     * </ul>
     *
     * @param projectVersion The project version for artifact validation
     * @param javaToolsConfig The javatools incoming configuration
     * @param xdkFileTree The XDK file tree (from extracted distribution)
     * @param logger Logger for diagnostic output
     * @return The resolved javatools.jar file
     * @throws GradleException if javatools.jar cannot be resolved
     */
    public static File resolveJavaTools(
            @NotNull final Provider<@NotNull String> projectVersion,
            @NotNull final Provider<@NotNull FileCollection> javaToolsConfig,
            @NotNull final Provider<@NotNull FileTree> xdkFileTree,
            @NotNull final Logger logger) {
        return resolveRuntime(projectVersion, javaToolsConfig, xdkFileTree, logger).launcherJar();
    }

    private static XtcLauncherRuntime resolveRuntimeFromFiles(
            final String source,
            final List<File> files,
            final String artifactVersion) {

        final var classpath = files.stream()
            .filter(file -> file.getName().endsWith(".jar"))
            .sorted(Comparator.comparing(File::getAbsolutePath))
            .toList();
        if (classpath.isEmpty()) {
            return null;
        }

        final var launcherJar = classpath.stream()
            .filter(file -> isValidJavaToolsArtifact(file, artifactVersion))
            .findFirst()
            .orElse(null);
        if (launcherJar == null) {
            return null;
        }

        validateAndReturn(launcherJar);
        return new XtcLauncherRuntime(source, launcherJar, classpath);
    }

    private static boolean sameClasspath(final List<File> left, final List<File> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!sameFile(left.get(i), right.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameFile(final File left, final File right) {
        if (left.equals(right)) {
            return true;
        }
        if (left.getName().equals(right.getName())) {
            return areIdenticalFiles(left, right);
        }
        return false;
    }

    private static void logResolvedRuntime(final Logger logger, final XtcLauncherRuntime runtime) {
        logger.info("[plugin] Resolved launcher runtime from '{}': {} entries", runtime.source(), runtime.classpath().size());
        runtime.classpath().forEach(file -> logger.info("[plugin]     {}", formatJarMetadata(file)));
    }

    private static boolean areIdenticalFiles(final File f1, final File f2) {
        try {
            return FileUtils.areIdenticalFiles(f1, f2);
        } catch (final IOException e) {
            throw failure(e, "Resolved non-identical multiple '{}' ('{}' and '{}')", XDK_JAVATOOLS_NAME_JAR, f1.getAbsolutePath(), f2.getAbsolutePath());
        }
    }

    private static File validateAndReturn(final File file) {
        if (!file.exists()) {
            throw failure("Resolved javatools.jar does not exist: {}", file.getAbsolutePath());
        }
        try (var zip = new ZipFile(file)) {
            if (zip.getEntry(XDK_JAVATOOLS_NAME_MANIFEST) == null) {
                throw failure("File is not a valid JAR: {}", file.getAbsolutePath());
            }
        } catch (final IOException e) {
            throw failure(e, "Failed to validate javatools.jar: {}", file.getAbsolutePath());
        }
        return file;
    }
}
