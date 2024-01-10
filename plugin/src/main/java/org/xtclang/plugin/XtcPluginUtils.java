package org.xtclang.plugin;

import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

import static java.util.Objects.requireNonNull;

/**
 * XTC Plugin Helper methods in a utility class.
 * <p>
 * TODO: Move the state independent/reentrant stuff from the ProjectDelegate and its subclasses to here.
 */
public final class XtcPluginUtils {
    private XtcPluginUtils() {
    }

    public static <T> Provider<? extends Iterable<? extends T>> singleArgumentIterableProvider(final Project project, final Provider<? extends T> arg) {
        return project.provider(() -> List.of(arg.get()));
    }

    public static List<String> argumentArrayToList(final String... args) {
        return Arrays.stream(ensureNotNull(args)).map(String::valueOf).toList();
    }

    private static Object[] ensureNotNull(final String... array) {
        Arrays.stream(array).forEach(e -> Objects.requireNonNull(e, "Arguments must never be null."));
        return array;
    }

    public static boolean areIdenticalFiles(final File f1, final File f2) throws IOException {
        return Files.mismatch(requireNonNull(f1).toPath(), requireNonNull(f2).toPath()) == -1L;
    }

    public static String readXdkVersionFromJar(final Logger logger, final String prefix, final File jar) {
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
}
