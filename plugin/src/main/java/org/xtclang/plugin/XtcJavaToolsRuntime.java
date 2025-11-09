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
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.Callable;
import java.util.zip.ZipFile;

import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_JAVATOOLS_INCOMING;
import static org.xtclang.plugin.XtcPluginConstants.XDK_JAVATOOLS_NAME_JAR;
import static org.xtclang.plugin.XtcPluginConstants.XDK_JAVATOOLS_NAME_MANIFEST;
import static org.xtclang.plugin.XtcPluginUtils.FileUtils.readXdkVersionFromJar;

/**
 * Runtime utility for accessing javatools classes throughout the plugin.
 *
 * <p>This class handles javatools.jar loading in two different scenarios:
 *
 * <p><b>Scenario 1: XDK Development (building XDK itself)</b>
 * <ul>
 *   <li>Plugin has compileOnly + runtimeOnly dependency on javatools</li>
 *   <li>Gradle automatically adds javatools.jar to plugin's classloader via runtimeOnly</li>
 *   <li>ensureJavaToolsInClasspath() does nothing (javatools already on classpath)</li>
 *   <li>withJavaTools() still needed for thread context classloader in forked processes</li>
 *   <li>resolveJavaTools() finds javatools.jar from composite build for forked processes</li>
 * </ul>
 *
 * <p><b>Scenario 2: Published Plugin Users (using XDK as dependency)</b>
 * <ul>
 *   <li>Plugin has compileOnly dependency on javatools (type info only)</li>
 *   <li>User project has XDK distribution as dependency (contains javatools.jar)</li>
 *   <li>ensureJavaToolsInClasspath() CRITICAL - loads javatools from XDK into plugin classloader</li>
 *   <li>withJavaTools() needed for thread context classloader in forked processes</li>
 *   <li>resolveJavaTools() finds javatools.jar from extracted XDK distribution</li>
 * </ul>
 *
 * <p><b>Why Three Methods:</b>
 * <ul>
 *   <li>resolveJavaTools() - Finds javatools.jar file path (needed for forked processes)</li>
 *   <li>ensureJavaToolsInClasspath() - Loads into plugin classloader (CRITICAL for published plugin)</li>
 *   <li>withJavaTools() - Sets thread context classloader (needed for in-process execution)</li>
 * </ul>
 */
public final class XtcJavaToolsRuntime {
    private static volatile ClassLoader javaToolsClassLoader = null;

    private XtcJavaToolsRuntime() {
        // Utility class
    }

    /**
     * Ensures javatools.jar is available to the plugin's classloader.
     * This is essential for published plugin users who have XDK as a dependency.
     *
     * <p><b>Why this is needed:</b>
     * <ul>
     *   <li>During XDK development: runtimeOnly dependency already provides javatools on classpath</li>
     *   <li>For published plugin users: This method loads javatools from XDK distribution into plugin classloader</li>
     * </ul>
     *
     * <p>IMPORTANT: This method is thread-safe and only loads javatools once.
     * After the first call, subsequent calls return immediately without any work.
     *
     * @param projectVersion The project version for artifact validation
     * @param javaToolsConfig The javatools incoming configuration
     * @param xdkFileTree The XDK file tree (from extracted distribution)
     * @param logger Logger for diagnostic output
     */
    public static synchronized void ensureJavaToolsInClasspath(
            @NotNull final Provider<@NotNull String> projectVersion,
            @NotNull final Provider<@NotNull FileCollection> javaToolsConfig,
            @NotNull final Provider<@NotNull FileTree> xdkFileTree,
            @NotNull final Logger logger) {

        if (javaToolsClassLoader != null) {
            logger.debug("[plugin] javatools.jar already loaded into classpath");
            return;
        }

        final File javaToolsJar = resolveJavaTools(projectVersion, javaToolsConfig, xdkFileTree, logger);

        try {
            // Create a shared classloader with javatools.jar and set as thread context classloader
            javaToolsClassLoader = createAndSetJavaToolsClassLoader(javaToolsJar, logger);
            logger.lifecycle("[plugin] ******* Loaded javatools.jar into plugin classpath: {}", javaToolsJar.getAbsolutePath());
            logger.info("[plugin] All javatools types now available throughout plugin");

        } catch (final Exception e) {
            throw new GradleException("[plugin] Failed to load javatools.jar into classpath: " + javaToolsJar.getAbsolutePath(), e);
        }
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

        final String artifactVersion = projectVersion.get();
        final var javaToolsFromConfig = javaToolsConfig.get().filter(file ->
                FileUtils.isValidJavaToolsArtifact(file, artifactVersion));
        final var javaToolsFromXdk = xdkFileTree.get().filter(file ->
                FileUtils.isValidJavaToolsArtifact(file, artifactVersion));

        logger.info("""
                [plugin] [javatools_runtime] javaToolsFromConfig files: {}
                [plugin] [javatools_runtime] javaToolsFromXdk files: {}
                """.trim(), javaToolsFromConfig.getFiles(), javaToolsFromXdk.getFiles());

        final File resolvedFromConfig = javaToolsFromConfig.isEmpty() ? null : javaToolsFromConfig.getSingleFile();
        final File resolvedFromXdk = javaToolsFromXdk.isEmpty() ? null : javaToolsFromXdk.getSingleFile();

        if (resolvedFromConfig == null && resolvedFromXdk == null) {
            throw new GradleException("[plugin] ERROR: Failed to resolve '" + XDK_JAVATOOLS_NAME_JAR +
                    "' from any configuration or dependency. " +
                    "Ensure the XDK dependency is configured correctly.");
        }

        logger.info("""
                [plugin] Check for '{}' in {} config and XDK (unpacked zip, or module collection) dependency, if present.
                [plugin]     Resolved to: [xdkJavaTools: {}, xdkContents: {}]
                """.trim(), XDK_JAVATOOLS_NAME_JAR, XDK_CONFIG_NAME_JAVATOOLS_INCOMING, resolvedFromConfig, resolvedFromXdk);

        final String versionConfig = readXdkVersionFromJar(resolvedFromConfig);
        final String versionXdk = readXdkVersionFromJar(resolvedFromXdk);

        if (resolvedFromConfig != null && resolvedFromXdk != null) {
            if (!versionConfig.equals(versionXdk) || !areIdenticalFiles(resolvedFromConfig, resolvedFromXdk)) {
                logger.warn("[plugin] Different '{}' files resolved, preferring the non-XDK version: {}",
                        XDK_JAVATOOLS_NAME_JAR, resolvedFromConfig.getAbsolutePath());
                return validateAndReturn(resolvedFromConfig);
            }
        }

        if (resolvedFromConfig != null) {
            logger.info("[plugin] Resolved unique '{}' from config/artifacts/dependencies: {} (version: {})",
                    XDK_JAVATOOLS_NAME_JAR, resolvedFromConfig.getAbsolutePath(), versionConfig);
            return validateAndReturn(resolvedFromConfig);
        }

        logger.info("[plugin] Resolved unique '{}' from XDK: {} (version: {})",
                XDK_JAVATOOLS_NAME_JAR, resolvedFromXdk.getAbsolutePath(), versionXdk);
        return validateAndReturn(resolvedFromXdk);
    }

    /**
     * Executes code with javatools classes available on the thread context classloader.
     * This is needed for in-process execution where javatools code may use Thread.currentThread().getContextClassLoader().
     *
     * <p>This method:
     * <ol>
     *   <li>Creates a URLClassLoader with javatools.jar</li>
     *   <li>Sets it as the thread context classloader</li>
     *   <li>Executes the provided callable</li>
     *   <li>Restores the original classloader</li>
     *   <li>Returns the result</li>
     * </ol>
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * ErrorList errors = XtcJavaToolsRuntime.withJavaTools(javaToolsJar, logger, () -> {
     *     ErrorList errorList = new ErrorList(100);
     *     // ... use javatools classes ...
     *     return errorList;
     * });
     * }</pre>
     *
     * @param javaToolsJar The javatools.jar file
     * @param logger Logger for diagnostic output
     * @param callable The code to execute with javatools on classpath
     * @param <T> The return type
     * @return The result from the callable
     * @throws Exception if the callable throws an exception
     */
    public static <T> T withJavaTools(
            @NotNull final File javaToolsJar,
            @NotNull final Logger logger,
            @NotNull final Callable<T> callable) throws Exception {

        return withJavaToolsClassLoader(javaToolsJar, logger, callable);
    }

    /**
     * Helper method that creates a URLClassLoader with javatools.jar, sets it as thread context classloader,
     * executes the callable, and restores the original classloader.
     *
     * @param javaToolsJar The javatools.jar file
     * @param logger Logger for diagnostic output
     * @param callable The code to execute with javatools on classpath
     * @param <T> The return type
     * @return The result from the callable
     * @throws Exception if the callable throws an exception
     */
    private static <T> T withJavaToolsClassLoader(
            @NotNull final File javaToolsJar,
            @NotNull final Logger logger,
            @NotNull final Callable<T> callable) throws Exception {

        final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            final URLClassLoader javaToolsClassLoader = createAndSetJavaToolsClassLoader(javaToolsJar, logger);
            logger.lifecycle("[plugin] ******* Added javatools.jar to runtime classpath: {}", javaToolsJar.getAbsolutePath());
            logger.info("[plugin] Set thread context classloader to javatools classloader");

            return callable.call();

        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            logger.info("[plugin] Restored original thread context classloader");
        }
    }

    /**
     * Creates a URLClassLoader with javatools.jar and sets it as the thread context classloader.
     * This is the common implementation used by both ensureJavaToolsInClasspath and withJavaTools.
     *
     * @param javaToolsJar The javatools.jar file
     * @param logger Logger for diagnostic output
     * @return The created URLClassLoader
     * @throws Exception if URL conversion fails
     */
    private static URLClassLoader createAndSetJavaToolsClassLoader(
            @NotNull final File javaToolsJar,
            @NotNull final Logger logger) throws Exception {

        final URL javaToolsUrl = javaToolsJar.toURI().toURL();
        final ClassLoader parentClassLoader = Thread.currentThread().getContextClassLoader();

        final URLClassLoader javaToolsClassLoader = new URLClassLoader(
                new URL[]{javaToolsUrl},
                parentClassLoader
        );

        Thread.currentThread().setContextClassLoader(javaToolsClassLoader);
        logger.debug("[plugin] Created URLClassLoader with javatools.jar: {}", javaToolsUrl);

        return javaToolsClassLoader;
    }

    private static boolean areIdenticalFiles(final File f1, final File f2) {
        try {
            return FileUtils.areIdenticalFiles(f1, f2);
        } catch (final IOException e) {
            throw new GradleException("[plugin] Resolved non-identical multiple '" + XDK_JAVATOOLS_NAME_JAR +
                    "' ('" + f1.getAbsolutePath() + "' and '" + f2.getAbsolutePath() + "')");
        }
    }

    private static File validateAndReturn(final File file) {
        if (!file.exists()) {
            throw new GradleException("[plugin] Resolved javatools.jar does not exist: " + file.getAbsolutePath());
        }

        try (var zip = new ZipFile(file)) {
            if (zip.getEntry(XDK_JAVATOOLS_NAME_MANIFEST) == null) {
                throw new GradleException("[plugin] File is not a valid JAR: " + file.getAbsolutePath());
            }
        } catch (final IOException e) {
            throw new GradleException("[plugin] Failed to validate javatools.jar: " + file.getAbsolutePath(), e);
        }

        return file;
    }
}
