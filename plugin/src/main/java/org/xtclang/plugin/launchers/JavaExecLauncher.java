package org.xtclang.plugin.launchers;

import static org.xtclang.plugin.XtcPluginConstants.XDK_JAVATOOLS_NAME_MANIFEST;
import static org.xtclang.plugin.XtcPluginConstants.XDK_JAVATOOLS_NAME_JAR;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_JAVATOOLS_INCOMING;
import static org.xtclang.plugin.XtcPluginUtils.FileUtils.readXdkVersionFromJar;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipFile;

import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.XtcPluginUtils.FileUtils;
import org.xtclang.plugin.tasks.XtcLauncherTask;

// TODO: Add more info to the LauncherException, and if we can reflect it out for the "javatools bundled with
//   the plugin" use case, let's do that. One thing I find that I would like very much is if the LauncherException
//   threw me the failed launcher command line as well.

/**
 * Launcher logic that runs the XTC launchers from classes on the classpath.
 */
public class JavaExecLauncher<E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>> extends XtcLauncher<E, T> {
    private final ExecOperations execOperations;
    protected final Provider<@NotNull String> toolchainExecutable;
    private final Provider<@NotNull String> projectVersion;
    private final Provider<@NotNull FileTree> xdkFileTree;
    private final Provider<@NotNull FileCollection> javaToolsConfig;

    public JavaExecLauncher(final T task, final Logger logger, final ExecOperations execOperations,
                            final Provider<@NotNull String> toolchainExecutable, final Provider<@NotNull String> projectVersion,
                            final Provider<@NotNull FileTree> xdkFileTree, final Provider<@NotNull FileCollection> javaToolsConfig) {
        super(task, logger);
        this.execOperations = execOperations;
        this.toolchainExecutable = toolchainExecutable;
        this.projectVersion = projectVersion;
        this.xdkFileTree = xdkFileTree;
        this.javaToolsConfig = javaToolsConfig;
    }

    @Override
    public ExecResult apply(final CommandLine cmd) {
        logger.info("[plugin] Launching task: {}}", this);

        final var javaToolsJar = resolveJavaTools();
        if (javaToolsJar == null) {
            throw new GradleException("[plugin] Failed to resolve '" + XDK_JAVATOOLS_NAME_JAR + "' in any classpath.");
        }

        logger.info("[plugin] {} (launcher: {}); Using '{}' in classpath from: {}", cmd.getIdentifier(), cmd.getClass(), XDK_JAVATOOLS_NAME_JAR, javaToolsJar);

        if (task.hasVerboseLogging()) {
            final var launchLine = cmd.toString(javaToolsJar);
            logger.lifecycle("[plugin] JavaExec command (launcher {}): {}", getClass().getSimpleName(), launchLine);
        }

        final var builder = resultBuilder(cmd);
        return createExecResult(builder.execResult(execOperations.javaexec(spec -> {
            redirectIo(spec);
            spec.classpath(javaToolsJar);
            spec.getMainClass().set(cmd.getMainClassName());
            spec.args(cmd.toList());
            spec.jvmArgs(cmd.getJvmArgs());
            spec.setIgnoreExitValue(true);
            
            // Use the project's configured Java toolchain instead of current JVM
            final var executable = toolchainExecutable.getOrNull();
            if (executable != null) {
                logger.info("[plugin] Using Java toolchain executable: {}", executable);
                spec.setExecutable(executable);
            } else {
                logger.warn("[plugin] No Java toolchain found, using default JVM");
            }
        })));
    }

    protected File resolveJavaTools() {
        // TODO: Way too complicated, just making absolutely sure that we don't mix class paths for e.g. XDK development, and something
        //   that is a distribution installed locally, thinking one is the other. This can be solved through artifact signing instead.

        /*
         * Verify that we have a javatools jar in our classpath. This will probably be simplified later, but is now
         * made to overly detailed ensure correctness for semantically versioned artifact declarations being
         * substituted by includedBuilds as well as being retrieved from a repository. These two worlds can
         * coexist in the XDK build, which bootstraps itself through the plugin from source code.
         *
         * The XDK build will refer to the Java tools dependencies by their incoming config, which will reflect
         * any changes done to Javatools during development. The rest of the world will refer to the "xdk" or
         * "xdkDistribution" dependencies, with the same versioned artifact name as parameter, which will
         * cause it to be picked from the XDK instead (or from the extracted XDK zip file).
         *
         * These two worlds can coexist in the XDK build, and are thus tested for correctness. The XDK
         * proper uses its "javatools" build, built from the latest source code. The manualTests project
         * asks for an XDK dependency for the Java tools instead, verifying that the just built XDK
         * resolves correctly.
         *
         * In the case of the dependency being present in both locations (should not happen, but is theoretically
         * possible if something has transitive dependencies), we verify that the jar files retrieved are
         * identical binaries. We should probably just switch to checking if exactly one jar file exists and
         * fail if there are multiple configurations. However, in order to ensure correctness of all dependencies,
         * we keep this code here for now. It will very likely go away in the future, and assume and assert that
         * there is only one configuration available to consume, containing the javatools.jar.
         */
        // NOTE: The projectVersion is resolved to whatever "version" is if we are not building the XKD does that work?
        final String artifactVersion = projectVersion.get();
        final var javaToolsFromConfig = javaToolsConfig.get().filter(file -> FileUtils.isValidJavaToolsArtifact(file, artifactVersion));
        final var javaToolsFromXdk = xdkFileTree.get().filter(file -> FileUtils.isValidJavaToolsArtifact(file, artifactVersion));

        logger.info("""
                [plugin] [java_exec_launcher] javaToolsFromConfig files: {}
                [plugin] [java_exec_launcher] javaToolsFromXdk files: {}
                """.trim(), javaToolsFromConfig.getFiles(), javaToolsFromXdk.getFiles());
        final File resolvedFromConfig = javaToolsFromConfig.isEmpty() ? null : javaToolsFromConfig.getSingleFile();
        final File resolvedFromXdk = javaToolsFromXdk.isEmpty() ? null : javaToolsFromXdk.getSingleFile();
        if (resolvedFromConfig == null && resolvedFromXdk == null) {
            throw new GradleException("[plugin] ERROR: Failed to resolve '" + XDK_JAVATOOLS_NAME_JAR + "' from any configuration or dependency.");
        }

        logger.info("""
                [plugin] Check for '{}' in {} config and XDK (unpacked zip, or module collection) dependency, if present.
                [plugin]     Resolved to: [xdkJavaTools: {}, xdkContents: {}]
                """.trim(), XDK_JAVATOOLS_NAME_JAR, XDK_CONFIG_NAME_JAVATOOLS_INCOMING, resolvedFromConfig, resolvedFromXdk);

        final String versionConfig = readXdkVersionFromJar(resolvedFromConfig);
        final String versionXdk = readXdkVersionFromJar(resolvedFromXdk);
        if (resolvedFromConfig != null && resolvedFromXdk != null) {
            if (!versionConfig.equals(versionXdk) || !areIdenticalFiles(resolvedFromConfig, resolvedFromXdk)) {
                logger.warn("[plugin] Different '{}' files resolved, preferring the non-XDK version: {}", XDK_JAVATOOLS_NAME_JAR, resolvedFromConfig.getAbsolutePath());
                return processJar(resolvedFromConfig);
            }
        }

        if (resolvedFromConfig != null) {
            assert resolvedFromXdk == null;
            logger.info("[plugin] Resolved unique '{}' from config/artifacts/dependencies: {} (version: {})", XDK_JAVATOOLS_NAME_JAR, resolvedFromConfig.getAbsolutePath(), versionConfig);
            return processJar(resolvedFromConfig);
        }

        logger.info("[plugin] Resolved unique '{}' from XDK: {} (version: {})", XDK_JAVATOOLS_NAME_JAR, resolvedFromXdk.getAbsolutePath(), versionXdk);
        return processJar(resolvedFromXdk);
    }

    private static boolean areIdenticalFiles(final File f1, final File f2) {
        try {
            return FileUtils.areIdenticalFiles(f1, f2);
        } catch (final IOException e) {
            throw new GradleException("[plugin] Resolved non-identical multiple '" + XDK_JAVATOOLS_NAME_JAR + "' ('" + f1.getAbsolutePath() + "' and '" + f2.getAbsolutePath() + "')");
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    private static boolean checkIsJarFile(final File file) {
        try (var zip = new ZipFile(file)) {
            return zip.getEntry(XDK_JAVATOOLS_NAME_MANIFEST) != null;
        } catch (final IOException e) {
            throw new GradleException("[plugin] Failed to read jar file: '" + file.getAbsolutePath() + "' (is the format correct?)");
        }
    }

    protected static File processJar(final File file) {
        assert file.exists();
        checkIsJarFile(file);
        return file;
    }

    /**
     * Execute multiple commands in parallel using concurrent javaexec operations.
     * This is useful when Gradle's parallel flag is enabled and multiple compilation
     * units need to be processed concurrently.
     *
     * @param commands List of commands to execute in parallel
     * @return List of execution results
     */
    public List<ExecResult> applyParallel(final List<CommandLine> commands) {
        logger.lifecycle("[plugin] Executing {} Java commands in parallel using javaexec", commands.size());

        // Use CompletableFuture to run multiple javaexec operations concurrently
        // Note: Gradle's ExecOperations is thread-safe and supports concurrent execution
        final List<CompletableFuture<ExecResult>> futures = commands.stream()
            .map(cmd -> CompletableFuture.supplyAsync(() -> apply(cmd)))
            .toList();

        // Wait for all to complete
        final CompletableFuture<Void> allOf = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture<?>[0])
        );

        try {
            allOf.get(); // Wait for all processes to complete
            return futures.stream().map(CompletableFuture::join).toList();
        } catch (final Exception e) {
            throw new GradleException("[plugin] Error during parallel execution", e);
        }
    }
}
