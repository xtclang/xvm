package org.xtclang.plugin.launchers;

import org.gradle.api.Project;
import org.gradle.process.ExecResult;
import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.XtcPluginUtils.FileUtils;
import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.tasks.XtcLauncherTask;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;

import static org.xtclang.plugin.XtcPluginConstants.JAR_MANIFEST_PATH;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_JAVATOOLS_INCOMING;
import static org.xtclang.plugin.XtcPluginUtils.FileUtils.readXdkVersionFromJar;

// TODO: Add more info to the LauncherException, and if we can reflect it out for the "javatools bundled with
//   the plugin" use case, let's do that. One thing I find that I would like very much is if the LauncherException
//   threw me the failed launcher command line as well.

/**
 * Launcher logic that runs the XTC launchers from classes on the classpath.
 */
public class JavaExecLauncher<E extends XtcLauncherTaskExtension, T extends XtcLauncherTask<E>> extends XtcLauncher<E, T> {
    public JavaExecLauncher(final Project project, final T task) {
        super(project, task);
    }

    @Override
    public ExecResult apply(final CommandLine cmd) {
        logger.info("{} Launching task: {}}", prefix, this);

        final var javaToolsJar = resolveJavaTools();
        if (javaToolsJar == null) {
            throw buildException("Failed to resolve 'javatools.jar' in any classpath.");
        }

        logger.info("{} {} (launcher: {}); Using 'javatools.jar' in classpath from: {}", prefix, cmd.getIdentifier(), cmd.getClass(), javaToolsJar);
        if (task.hasVerboseLogging()) {
            logger.lifecycle("{} JavaExec command (launcher {}): {}", prefix, getClass().getSimpleName(), cmd.toString(javaToolsJar));
        }

        final var builder = resultBuilder(cmd);
        return createExecResult(builder.execResult(project.getProject().javaexec(spec -> {
            redirectIo(builder, spec);
            spec.classpath(javaToolsJar);
            spec.getMainClass().set(cmd.getMainClassName());
            spec.args(cmd.toList());
            spec.jvmArgs(cmd.getJvmArgs());
            spec.setIgnoreExitValue(true);
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
        final var javaToolsFromConfig = task.filesFromConfigs(true, XDK_CONFIG_NAME_JAVATOOLS_INCOMING).filter(FileUtils::isValidJavaToolsArtifact);
        final var javaToolsFromXdk = project.fileTree(XtcProjectDelegate.getXdkContentsDir(project)).filter(FileUtils::isValidJavaToolsArtifact);

        logger.info("""            
                {} javaToolsFromConfig files: {}
                {} javaToolsFromXdk files: {}
                """.trim(), prefix, javaToolsFromConfig.getFiles(), prefix, javaToolsFromXdk.getFiles());

        final File resolvedFromConfig = javaToolsFromConfig.isEmpty() ? null : javaToolsFromConfig.getSingleFile();
        final File resolvedFromXdk = javaToolsFromXdk.isEmpty() ? null : javaToolsFromXdk.getSingleFile();
        if (resolvedFromConfig == null && resolvedFromXdk == null) {
            throw buildException("ERROR: Failed to resolve 'javatools.jar' from any configuration or dependency.");
        }

        logger.info("""
                {} Check for 'javatools.jar' in {} config and XDK (unpacked zip, or module collection) dependency, if present.
                {}     Resolved to: [xdkJavaTools: {}, xdkContents: {}]
                """.trim(), prefix, XDK_CONFIG_NAME_JAVATOOLS_INCOMING, prefix, resolvedFromConfig, resolvedFromXdk);

        final String versionConfig = readXdkVersionFromJar(resolvedFromConfig);
        final String versionXdk = readXdkVersionFromJar(resolvedFromXdk);
        if (resolvedFromConfig != null && resolvedFromXdk != null) {
            if (!versionConfig.equals(versionXdk) || !areIdenticalFiles(resolvedFromConfig, resolvedFromXdk)) {
                logger.warn("{} Different 'javatools.jar' files resolved, preferring the non-XDK version: {}", prefix, resolvedFromConfig.getAbsolutePath());
                return processJar(resolvedFromConfig);
            }
        }

        if (resolvedFromConfig != null) {
            assert resolvedFromXdk == null;
            logger.info("{} Resolved unique 'javatools.jar' from config/artifacts/dependencies: {} (version: {})", prefix, resolvedFromConfig.getAbsolutePath(), versionConfig);
            return processJar(resolvedFromConfig);
        }

        logger.info("{} Resolved unique 'javatools.jar' from XDK: {} (version: {})", prefix, resolvedFromXdk.getAbsolutePath(), versionXdk);
        return processJar(resolvedFromXdk);
    }

    private boolean areIdenticalFiles(final File f1, final File f2) {
        try {
            return FileUtils.areIdenticalFiles(f1, f2);
        } catch (final IOException e) {
            throw buildException("{} Resolved non-identical multiple 'javatools.jar' ('{}' and '{}')",
                prefix, f1.getAbsolutePath(), f2.getAbsolutePath());
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean checkIsJarFile(final File file) {
        try (final ZipFile zip = new ZipFile(file)) {
            return zip.getEntry(JAR_MANIFEST_PATH) != null;
        } catch (final IOException e) {
            throw buildException("Failed to read jar file: '{}' (is the format correct?)", file.getAbsolutePath());
        }
    }

    protected File processJar(final File file) {
        assert file.exists();
        checkIsJarFile(file);
        return file;
    }
}
