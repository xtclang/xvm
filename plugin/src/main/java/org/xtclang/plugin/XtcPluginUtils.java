package org.xtclang.plugin;

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static java.util.Objects.requireNonNull;
import static org.xtclang.plugin.XtcPluginConstants.XDK_JAVATOOLS_ARTIFACT_ID;
import static org.xtclang.plugin.XtcPluginConstants.XTC_MAGIC;
import static org.xtclang.plugin.XtcPluginConstants.XTC_MODULE_FILE_EXTENSION;

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

    public static String capitalize(final String string) {
        return Character.toUpperCase(string.charAt(0)) + string.substring(1);
    }

    public static final class FileUtils {
        private FileUtils() {
        }

        /**
         * Check that a file is a valid JavaTools artifact. This means that the file should exist as a regular
         * file, be readable, have a name with the format javatools[version-and-extension].jar, and that its
         * manifest is a valid XTC/XDK manifest, including semantic version information for the XDK to which
         * it belongs.
         *
         * @param file file to check
         * @return true if the file is a valid JavaTools jar file, false otherwise.
         */
        public static boolean isValidJavaToolsArtifact(final File file) {
            return hasJarExtension(file) && file.getName().startsWith(XDK_JAVATOOLS_ARTIFACT_ID) && readXdkVersionFromJar(file) != null;
        }

        /**
         * Check that a file is a valid XTC Module. This means that it should exist, be readable, and
         * contain the correct magic at the first bytes. We don't currently check if it's an XDK version,
         * or if it's version has a particular format.
         *
         * @param file file to check
         * @return true if the file seems to be a valid XTC module, false otherwise
         */
        public static boolean isValidXtcModule(final File file) {
            return isValidXtcModule(file, true);
        }

        @SuppressWarnings("SameParameterValue")
        private static boolean isValidXtcModule(final File file, final boolean checkMagic) {
            if (!file.exists() || !file.isFile() || !hasFileExtension(file, XTC_MODULE_FILE_EXTENSION)) {
                return false;
            }
            if (!checkMagic) {
                return true;
            }
            try (final var in = new DataInputStream(new FileInputStream(file))) {
                return (in.readInt() & 0xffff_ffffL) == XTC_MAGIC;
            } catch (final IOException e) {
                return false;
            }
        }

        public static File checkXtcModule(final File file) {
            return checkXtcModule(file, true);
        }

        @SuppressWarnings("SameParameterValue")
        private static File checkXtcModule(final File file, final boolean checkMagic) {
            if (!isValidXtcModule(file, checkMagic)) {
                throw new XtcBuildRuntimeException("Processed '{}' as an XTC module, but it is not.", file.getAbsolutePath());
            }
            return file;
        }

        /**
         * Reads the XDK version from a jar manifest.
         *
         * @param file Jar file from which to read
         * @return the XDK version string, as stored in the jar, or null if file not found, or entry could not be parsed.
         */
        public static String readXdkVersionFromJar(final File file) {
            if (file == null) {
                return null;
            }
            final var path = file.getAbsolutePath();
            assert file.isFile();
            try (final var jarFile = new JarFile(file)) {
                final Manifest m = jarFile.getManifest();
                final var implVersion = m.getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION);
                if (implVersion == null) {
                    throw new XtcBuildRuntimeException("Invalid manifest entries found in '{}'", path);
                }
                return implVersion.toString();
            } catch (final IOException e) {
                throw new XtcBuildRuntimeException(e, "Not a valid 'javatools.jar': '{}'", path);
            }
        }

        private static boolean hasJarExtension(final File file) {
            return hasFileExtension(file, "jar");
        }

        public static boolean hasFileExtension(final File file, final String extension) {
            return getFileExtension(file).equalsIgnoreCase(extension);
        }

        public static String getFileExtension(final File file) {
            final String name = file.getName();
            final int dot = name.lastIndexOf('.');
            return dot == -1 ? "" : name.substring(dot + 1);
        }

        public static boolean areIdenticalFiles(final File f1, final File f2) throws IOException {
            return Files.mismatch(requireNonNull(f1).toPath(), requireNonNull(f2).toPath()) == -1L;
        }
    }
}
