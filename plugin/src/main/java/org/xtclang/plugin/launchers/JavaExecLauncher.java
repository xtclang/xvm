package org.xtclang.plugin.launchers;

import static org.xtclang.plugin.XtcPluginConstants.JAR_MANIFEST_PATH;
import static org.xtclang.plugin.XtcPluginConstants.JAVATOOLS_JAR_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_JAVATOOLS_INCOMING;
import static org.xtclang.plugin.XtcPluginUtils.FileUtils.readXdkVersionFromJar;

import java.io.File;
import java.io.IOException;

import java.util.zip.ZipFile;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.XtcPluginUtils.FileUtils;
import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.tasks.XtcLauncherTask;

// TODO: Add more info to the LauncherException, and if we can reflect it out for the "javatools bundled with
//   the plugin" use case, let's do that. One thing I find that I would like very much is if the LauncherException
//   threw me the failed launcher command line as well.

/**
 * Launcher logic that runs the XTC launchers from classes on the classpath.
 */
public class JavaExecLauncher<E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>> extends XtcLauncher<E, T> {
    private final ExecOperations execOperations;
    private final Provider<String> toolchainExecutable;
    private final Provider<String> projectVersion;
    private final Provider<FileTree> xdkFileTree;
    private final Provider<FileCollection> javaToolsConfig;
    
    public JavaExecLauncher(final Project project, final T task, final ExecOperations execOperations) {
        super(project, task);
        this.execOperations = execOperations;
        
        // Resolve toolchain at configuration time, not execution time
        this.toolchainExecutable = project.provider(() -> {
            final var javaExtension = project.getExtensions().findByType(org.gradle.api.plugins.JavaPluginExtension.class);
            if (javaExtension != null) {
                final var toolchains = project.getExtensions().getByType(org.gradle.jvm.toolchain.JavaToolchainService.class);
                final var launcher = toolchains.launcherFor(javaExtension.getToolchain());
                return launcher.get().getExecutablePath().toString();
            }
            return null;
        });
        
        // Resolve project version at configuration time, not execution time
        this.projectVersion = project.provider(() -> project.getVersion().toString());
        
        // Resolve XDK file tree at configuration time, not execution time
        this.xdkFileTree = XtcProjectDelegate.getXdkContentsDir(project).map(project::fileTree);
        
        // Resolve JavaTools configuration at configuration time, not execution time
        this.javaToolsConfig = project.provider(() -> 
            project.files(project.getConfigurations().getByName(XDK_CONFIG_NAME_JAVATOOLS_INCOMING)));
    }
    
    public JavaExecLauncher(final T task, final Logger logger, final ExecOperations execOperations,
                           final Provider<String> toolchainExecutable, final Provider<String> projectVersion,
                           final Provider<FileTree> xdkFileTree, final Provider<FileCollection> javaToolsConfig) {
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
            throw new GradleException("[plugin] Failed to resolve '" + JAVATOOLS_JAR_NAME + "' in any classpath.");
        }

        logger.info("[plugin] {} (launcher: {}); Using '{}' in classpath from: {}", cmd.getIdentifier(), cmd.getClass(), JAVATOOLS_JAR_NAME, javaToolsJar);

        if (task.hasVerboseLogging()) {
            final var launchLine = cmd.toString(javaToolsJar);
            logger.lifecycle("[plugin] JavaExec command (launcher {}):", getClass().getSimpleName());
            logger.lifecycle("[plugin]     {}", launchLine);
        }

        final var builder = resultBuilder(cmd);
        return createExecResult(builder.execResult(execOperations.javaexec(spec -> {
            redirectIo(builder, spec);
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

    private File resolveJavaTools() {
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
        final String artifactVersion = projectVersion.get();
        final var javaToolsFromConfig = javaToolsConfig.get().filter(file -> FileUtils.isValidJavaToolsArtifact(file, artifactVersion));
        final var javaToolsFromXdk = xdkFileTree.get().filter(file -> FileUtils.isValidJavaToolsArtifact(file, artifactVersion));

        logger.info("""            
                [plugin] javaToolsFromConfig files: {}
                [plugin] javaToolsFromXdk files: {}
                """.trim(), javaToolsFromConfig.getFiles(), javaToolsFromXdk.getFiles());

        final File resolvedFromConfig = javaToolsFromConfig.isEmpty() ? null : javaToolsFromConfig.getSingleFile();
        final File resolvedFromXdk = javaToolsFromXdk.isEmpty() ? null : javaToolsFromXdk.getSingleFile();
        if (resolvedFromConfig == null && resolvedFromXdk == null) {
            throw new GradleException("[plugin] ERROR: Failed to resolve '" + JAVATOOLS_JAR_NAME + "' from any configuration or dependency.");
        }

        logger.info("""
                [plugin] Check for '{}' in {} config and XDK (unpacked zip, or module collection) dependency, if present.
                [plugin]     Resolved to: [xdkJavaTools: {}, xdkContents: {}]
                """.trim(), JAVATOOLS_JAR_NAME, XDK_CONFIG_NAME_JAVATOOLS_INCOMING, resolvedFromConfig, resolvedFromXdk);

        final String versionConfig = readXdkVersionFromJar(resolvedFromConfig);
        final String versionXdk = readXdkVersionFromJar(resolvedFromXdk);
        if (resolvedFromConfig != null && resolvedFromXdk != null) {
            if (!versionConfig.equals(versionXdk) || !areIdenticalFiles(resolvedFromConfig, resolvedFromXdk)) {
                logger.warn("[plugin] Different '{}' files resolved, preferring the non-XDK version: {}",
                    JAVATOOLS_JAR_NAME, resolvedFromConfig.getAbsolutePath());
                return processJar(resolvedFromConfig);
            }
        }

        if (resolvedFromConfig != null) {
            assert resolvedFromXdk == null;
            logger.info("[plugin] Resolved unique '{}' from config/artifacts/dependencies: {} (version: {})",
                JAVATOOLS_JAR_NAME, resolvedFromConfig.getAbsolutePath(), versionConfig);
            return processJar(resolvedFromConfig);
        }

        logger.info("[plugin] Resolved unique '{}' from XDK: {} (version: {})", JAVATOOLS_JAR_NAME, resolvedFromXdk.getAbsolutePath(), versionXdk);
        return processJar(resolvedFromXdk);
    }

    private boolean areIdenticalFiles(final File f1, final File f2) {
        try {
            return FileUtils.areIdenticalFiles(f1, f2);
        } catch (final IOException e) {
            throw new GradleException("[plugin] Resolved non-identical multiple '" + JAVATOOLS_JAR_NAME + "' ('" + f1.getAbsolutePath() + "' and '" + f2.getAbsolutePath() + "')");
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean checkIsJarFile(final File file) {
        try (var zip = new ZipFile(file)) {
            return zip.getEntry(JAR_MANIFEST_PATH) != null;
        } catch (final IOException e) {
            throw new GradleException("[plugin] Failed to read jar file: '" + file.getAbsolutePath() + "' (is the format correct?)");
        }
    }

    protected File processJar(final File file) {
        assert file.exists();
        checkIsJarFile(file);
        return file;
    }
}
