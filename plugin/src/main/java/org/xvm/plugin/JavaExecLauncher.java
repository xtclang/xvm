package org.xvm.plugin;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.process.ExecResult;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;

import static java.util.Objects.requireNonNull;
import static org.xvm.plugin.Constants.JAR_MANIFEST_PATH;
import static org.xvm.plugin.Constants.JAVATOOLS_ARTIFACT_ID;
import static org.xvm.plugin.Constants.XTC_CONFIG_NAME_JAVATOOLS_INCOMING;

/**
 * Launcher logic that runs the XTC launchers from classes on the classpath.
 */
public class JavaExecLauncher extends XtcLauncher {

    private final XtcProjectDelegate delegate;

    JavaExecLauncher(final XtcProjectDelegate delegate) {
        super(delegate.getProject());
        this.delegate = delegate;
    }

    @Override
    public ExecResult apply(final CommandLine args) {
        final var javaToolsJar = resolveJavaTools();
        if (javaToolsJar == null) {
            throw buildException("Failed to resolve javatools.jar in any classpath.");
        }
        info("{} '{}' {}; Using javatools.jar in classpath from: {}", prefix, args.getIdentifier(), args.getClass(), javaToolsJar.getAbsolutePath());
        lifecycle("{} JavaExec command: {}", prefix, args.toString(javaToolsJar));

        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        try {
            final var result = project.getProject().javaexec(spec -> {
                spec.setStandardInput(System.in); // Not coupled by default for JavaExec
                //spec.setStandardOutput(out);
                //spec.setErrorOutput(err);
                spec.classpath(javaToolsJar);
                spec.getMainClass().set(args.getMainClassName());
                spec.args(args.toList());
                spec.jvmArgs(args.getJvmArgs());
                spec.setIgnoreExitValue(true);
            });
            final var exitValue = result.getExitValue();
            final boolean success = exitValue == 0;
            log(success ? LogLevel.INFO : LogLevel.ERROR, "{} '{}' JavaExec return value: {}", prefix, args.getMainClassName(), exitValue);
            return result;
        } finally {
            showOutput(args, out.toString(), err.toString());
        }
    }

    String readXdkVersionFromJar(final File jar) {
        return readXdkVersionFromJar(logger, prefix, jar);
    }

    static String readXdkVersionFromJar(final Logger logger, final String prefix, final File jar) {
        if (jar == null) {
            return null;
        }
        try (final var jarFile = new JarFile(jar)) {
            final Manifest m = jarFile.getManifest();
            final var implVersion = m.getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION);
            if (implVersion == null) {
                throw new IOException("Invalid manifest entries found in " + jar.getAbsolutePath());
            }
            logger.info("{} Detected valid javatools.jar: {} (XTC Manifest Version: {})", prefix, jar.getAbsolutePath(), implVersion);
            return implVersion.toString();
        } catch (final IOException e) {
            logger.error("{} Expected " + jar.getAbsolutePath() + " to be a jar file, with a readable manifest: {}", prefix, e.getMessage());
            return null;
        }
    }

    private boolean isJavaToolsJar(final File file) {
        final boolean ok = "jar".equalsIgnoreCase(getFileExtension(file)) &&
                file.getName().startsWith(JAVATOOLS_ARTIFACT_ID) &&
                readXdkVersionFromJar(file) != null;
        info("{} isJavaToolsJar({}) = {}", prefix, file.getAbsolutePath(), ok);
        return ok;
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
        // TODO: Way too complicated.
        final var javaToolsFromConfig =
                delegate.filesFrom(true, XTC_CONFIG_NAME_JAVATOOLS_INCOMING).filter(this::isJavaToolsJar);
        final var javaToolsFromXdk =
                project.getProject().fileTree(delegate.getXdkContentsDir()).filter(this::isJavaToolsJar);

        final File resolvedFromConfig = javaToolsFromConfig.isEmpty() ? null : javaToolsFromConfig.getSingleFile();
        final File resolvedFromXdk = javaToolsFromXdk.isEmpty() ? null : javaToolsFromXdk.getSingleFile();
        if (resolvedFromConfig == null && resolvedFromXdk == null) {
            System.err.println(javaToolsFromXdk.getAsPath());
            throw buildException("ERROR: Failed to resolve javatools.jar from any configuration or dependency.");
        }

        info("""
            {} Check for javatools.jar in {} config and XDK dependency, if present.
            {}     Resolved to: [xtcJavaTools: {}, xdkContents: {}]
            """.trim(),
                prefix, XTC_CONFIG_NAME_JAVATOOLS_INCOMING,
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
            info("{} Resolved unique javatools.jar from config/artifacts/dependencies: {} (version: {})", prefix, resolvedFromConfig.getAbsolutePath(), versionConfig);
            return processJar(resolvedFromConfig);
        }

        info("{} Resolved unique javatools.jar from XDK: {} (version: {})", prefix, resolvedFromXdk.getAbsolutePath(), versionXdk);
        return processJar(resolvedFromXdk);
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean checkIsJarFile(final File file) {
        try (final ZipFile zip = new ZipFile(file)) {
            return zip.getEntry(JAR_MANIFEST_PATH) != null;
        } catch (final IOException e) {
            throw buildException("Failed to read jar file: " + file.getAbsolutePath() + " (is the format correct?)");
        }
    }

    protected File processJar(final File file) {
        assert file.exists();
        checkIsJarFile(file);
        return file;
    }
}
