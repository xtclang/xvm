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
 * <p>This class bridges the gap between compile-time type information (from compileOnly dependency)
 * and runtime class loading (from resolved javatools.jar). It provides:
 * <ul>
 *   <li>javatools.jar resolution from XDK build or distribution</li>
 *   <li>Dynamic classloader creation with javatools classes</li>
 *   <li>Helper methods to execute code with javatools on the classpath</li>
 * </ul>
 *
 * <p><b>Usage Pattern:</b>
 * <pre>{@code
 * // Resolve javatools once
 * File javaToolsJar = XtcJavaToolsRuntime.resolveJavaTools(...);
 *
 * // Execute code with javatools classes available
 * XtcJavaToolsRuntime.withJavaTools(javaToolsJar, logger, () -> {
 *     // Can now use javatools classes directly
 *     ErrorList errors = new ErrorList(100);
 *     Compiler.launch(args);
 *     return errors;
 * });
 * }</pre>
 *
 * <p><b>How It Works:</b>
 * <ol>
 *   <li>At compile-time: Plugin has compileOnly dependency on javatools (type info only)</li>
 *   <li>At runtime: Plugin resolves javatools.jar from XDK build or distribution</li>
 *   <li>When needed: Creates URLClassLoader with javatools.jar and executes code</li>
 * </ol>
 *
 * <p>This approach works in both scenarios:
 * <ul>
 *   <li>XDK development: javatools.jar from local build (composite build)</li>
 *   <li>Third-party (xtc-app-template): javatools.jar from extracted XDK distribution</li>
 * </ul>
 */
public final class XtcJavaToolsRuntime {

    private static volatile boolean javaToolsLoadedIntoClasspath = false;

    private XtcJavaToolsRuntime() {
        // Utility class
    }

    /**
     * Ensures javatools.jar is loaded into the current classloader.
     * After this method completes successfully, all javatools types can be used directly
     * throughout the plugin without any reflection or special classloader handling.
     *
     * <p>This method is idempotent - it only loads javatools once, even if called multiple times.
     *
     * @param projectVersion The project version for artifact resolution
     * @param javaToolsConfig The javatools incoming configuration
     * @param xdkFileTree The XDK file tree (from extracted distribution)
     * @param logger Logger for diagnostic output
     * @throws GradleException if javatools.jar cannot be resolved or loaded
     */
    public static synchronized void ensureJavaToolsInClasspath(
            @NotNull final Provider<@NotNull String> projectVersion,
            @NotNull final Provider<@NotNull FileCollection> javaToolsConfig,
            @NotNull final Provider<@NotNull FileTree> xdkFileTree,
            @NotNull final Logger logger) {

        if (javaToolsLoadedIntoClasspath) {
            logger.debug("[plugin] javatools.jar already loaded into classpath");
            return;
        }

        final var javaToolsJar = resolveJavaTools(projectVersion, javaToolsConfig, xdkFileTree, logger);
        final var currentClassLoader = XtcJavaToolsRuntime.class.getClassLoader();

        if (!(currentClassLoader instanceof URLClassLoader)) {
            throw new GradleException("[plugin] Cannot load javatools: classloader is not URLClassLoader: " + currentClassLoader.getClass().getName());
        }

        try {
            final URL javaToolsUrl = javaToolsJar.toURI().toURL();
            final java.lang.reflect.Method addUrlMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addUrlMethod.setAccessible(true);
            addUrlMethod.invoke(currentClassLoader, javaToolsUrl);
            javaToolsLoadedIntoClasspath = true;
            logger.info("[plugin] ******* Loaded javatools.jar into plugin classpath: {}", javaToolsJar.getAbsolutePath());

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

        final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            final URL javaToolsUrl = javaToolsJar.toURI().toURL();
            final URLClassLoader javaToolsClassLoader = new URLClassLoader(
                    new URL[]{javaToolsUrl},
                    originalClassLoader
            );

            Thread.currentThread().setContextClassLoader(javaToolsClassLoader);
            logger.lifecycle("[plugin] ******* Added javatools.jar to runtime classpath: {}", javaToolsJar.getAbsolutePath());
            logger.info("[plugin] Set thread context classloader to javatools: {}", javaToolsUrl);

            return callable.call();

        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            logger.info("[plugin] Restored original thread context classloader");
        }
    }

    /**
     * Creates a URLClassLoader with javatools.jar for manual class loading.
     *
     * <p>Use this when you need more control over class loading, such as:
     * <ul>
     *   <li>Loading specific classes by name</li>
     *   <li>Keeping the classloader for multiple operations</li>
     *   <li>Isolating javatools classes from other code</li>
     * </ul>
     *
     * <p><b>Example:</b>
     * <pre>{@code
     * try (URLClassLoader loader = XtcJavaToolsRuntime.createJavaToolsClassLoader(jar, logger)) {
     *     Class<?> compilerClass = loader.loadClass("org.xvm.tool.Compiler");
     *     // ... use the class ...
     * }
     * }</pre>
     *
     * @param javaToolsJar The javatools.jar file
     * @param logger Logger for diagnostic output
     * @return A URLClassLoader with javatools.jar
     * @throws IOException if the JAR URL cannot be created
     */
    public static URLClassLoader createJavaToolsClassLoader(
            @NotNull final File javaToolsJar,
            @NotNull final Logger logger) throws IOException {

        final URL javaToolsUrl = javaToolsJar.toURI().toURL();
        logger.debug("[plugin] Creating javatools classloader with: {}", javaToolsUrl);

        return new URLClassLoader(
                new URL[]{javaToolsUrl},
                Thread.currentThread().getContextClassLoader()
        );
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
