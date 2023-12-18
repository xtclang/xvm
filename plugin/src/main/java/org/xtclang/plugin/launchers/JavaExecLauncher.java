package org.xtclang.plugin.launchers;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.process.ExecResult;
import org.xtclang.plugin.ProjectDelegate;
import org.xtclang.plugin.XtcProjectDelegate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;

import static java.util.Objects.requireNonNull;
import static org.xtclang.plugin.XtcPluginConstants.JAR_MANIFEST_PATH;
import static org.xtclang.plugin.XtcPluginConstants.XDK_JAVATOOLS_ARTIFACT_ID;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_JAVATOOLS_INCOMING;

/**
 * Launcher logic that runs the XTC launchers from classes on the classpath.
 */
public class JavaExecLauncher extends XtcLauncher {

    JavaExecLauncher(final Project project, final String mainClass, final boolean logOutputs) {
        super(project, mainClass, logOutputs);
    }

    @Override
    public ExecResult apply(final CommandLine cmd) {
        final var javaToolsJar = resolveJavaTools();
        if (javaToolsJar == null) {
            throw buildException("Failed to resolve 'javatools.jar' in any classpath.");
        }
        info("{} '{}' {}; Using 'javatools.jar' in classpath from: {}", prefix, cmd.getIdentifier(), cmd.getClass(), javaToolsJar.getAbsolutePath());
        if (hasVerboseLogging()) {
            lifecycle("{} JavaExec command: {}", prefix, cmd.toString(javaToolsJar));
        }

        final var builder = resultBuilder(cmd);
        return createExecResult(builder.execResult(
            project.getProject().javaexec(spec -> {
                if (logOutputs) {
                    spec.setStandardOutput(builder.getOut());
                    spec.setErrorOutput(builder.getErr());
                }
                spec.classpath(javaToolsJar);
                spec.getMainClass().set(cmd.getMainClassName());
                spec.args(cmd.toList());
                spec.jvmArgs(cmd.getJvmArgs());
                spec.setIgnoreExitValue(true);
            })));
    }

    private String readXdkVersionFromJar(final File jar) {
        return readXdkVersionFromJar(logger, prefix, jar);
    }

    private static String readXdkVersionFromJar(final Logger logger, final String prefix, final File jar) {
        if (jar == null) {
            return null;
        }
        try (final var jarFile = new JarFile(jar)) {
            final Manifest m = jarFile.getManifest();
            final var implVersion = m.getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION);
            if (implVersion == null) {
                throw new IOException("Invalid manifest entries found in " + jar.getAbsolutePath());
            }
            logger.info("{} Detected valid 'javatools.jar': {} (XTC Manifest Version: {})", prefix, jar.getAbsolutePath(), implVersion);
            return implVersion.toString();
        } catch (final IOException e) {
            logger.error("{} Expected '{}' to be a jar file, with a readable manifest: {}", prefix, jar.getAbsolutePath(), e.getMessage());
            return null;
        }
    }

    private boolean isJavaToolsJar(final File file) {
        if (ProjectDelegate.hasFileExtension(file, "jar") &&
            file.getName().startsWith(XDK_JAVATOOLS_ARTIFACT_ID) &&
            readXdkVersionFromJar(file) != null) {
            info("{} Detected 'javatools.jar' file at: '{}'", prefix, file.getAbsolutePath());
            return true;
        }
        return false;
    }

    private boolean identical(final File f1, final File f2) {
        try {
            final long mismatch = Files.mismatch(requireNonNull(f1).toPath(), requireNonNull(f2).toPath());
            if (mismatch == -1L) {
                return true;
            }
            warn("{} resolves multiple javatools.jar that are different: ({} != {}, mismatch at byte: {})", prefix, f1.getAbsolutePath(), f2.getAbsolutePath(), mismatch);
            final long l1 = f1.length();
            final long l2 = f2.length();
            if (l1 != l2) {
                warn("{}   {} bytes != {} bytes", prefix, f1.length(), f2.length());
            }
            final long lm1 = f1.lastModified();
            final long lm2 = f2.lastModified();
            if (lm1 != lm2) {
                warn("{}   {} lastModified != {} lastModified?", prefix, f1.lastModified(), f2.lastModified());
            }
            return false;
        } catch (final IOException e) {
            throw buildException(e.getMessage(), e);
        }
    }

    private File resolveJavaTools() {
        // TODO: Way too complicated, just making absolutely sure that we don't mix classpaths for e.g. XDK development, and something
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
         *
         * TODO: It might be the case that the IDE integrated runner for the Javatools project still has
         *   issues by referring to a finished jar artifact, and not somehow to the included build, but
         *   we believe IntelliJ may fix that because it understands correctly written Gradle build script
         *   semantics. Verify this.
         */
        final var javaToolsFromConfig = filesFrom(true, XDK_CONFIG_NAME_JAVATOOLS_INCOMING).filter(this::isJavaToolsJar);
        final var javaToolsFromXdk =
                project.getProject().fileTree(XtcProjectDelegate.getXdkContentsDir(project)).filter(this::isJavaToolsJar);
        System.err.println("** javatools config: " + javaToolsFromConfig.getFiles());
        System.err.println("** javatools xdk   : " + javaToolsFromXdk.getFiles());

        final File resolvedFromConfig = javaToolsFromConfig.isEmpty() ? null : javaToolsFromConfig.getSingleFile();
        final File resolvedFromXdk = javaToolsFromXdk.isEmpty() ? null : javaToolsFromXdk.getSingleFile();
        if (resolvedFromConfig == null && resolvedFromXdk == null) {
            throw buildException("ERROR: Failed to resolve 'javatools.jar' from any configuration or dependency.");
        }

        info("""
            {} Check for javatools.jar in {} config and XDK (unpacked zip, or module collection) dependency, if present.
            {}     Resolved to: [xdkJavaTools: {}, xdkContents: {}]
            """.trim(),
                prefix, XDK_CONFIG_NAME_JAVATOOLS_INCOMING,
                prefix, resolvedFromConfig, resolvedFromXdk);

        final String versionConfig = readXdkVersionFromJar(resolvedFromConfig);
        final String versionXdk = readXdkVersionFromJar(resolvedFromXdk);
        if (resolvedFromConfig != null && resolvedFromXdk != null) {
            if (!versionConfig.equals(versionXdk) || !identical(resolvedFromConfig, resolvedFromXdk)) {
                warn("{} Different javatools in resolved files, preferring the non-XDK version: {}", prefix, resolvedFromConfig.getAbsolutePath());
                return processJar(resolvedFromConfig);
            }
        }

        if (resolvedFromConfig != null) {
            assert resolvedFromXdk == null;
            info("{} Resolved unique 'javatools.jar' from config/artifacts/dependencies: {} (version: {})", prefix, resolvedFromConfig.getAbsolutePath(), versionConfig);
            return processJar(resolvedFromConfig);
        }

        info("{} Resolved unique 'javatools.jar' from XDK: {} (version: {})", prefix, resolvedFromXdk.getAbsolutePath(), versionXdk);
        return processJar(resolvedFromXdk);
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean checkIsJarFile(final File file) {
        try (final ZipFile zip = new ZipFile(file)) {
            return zip.getEntry(JAR_MANIFEST_PATH) != null;
        } catch (final IOException e) {
            throw buildException("Failed to read jar file: '" + file.getAbsolutePath() + "' (is the format correct?)");
        }
    }

    protected File processJar(final File file) {
        assert file.exists();
        checkIsJarFile(file);
        return file;
    }
}
