package org.xtclang.plugin;

import static java.time.format.DateTimeFormatter.ofPattern;
import static java.util.Objects.requireNonNull;

import static org.xtclang.plugin.XtcPluginConstants.XDK_JAVATOOLS_NAME_JAR;
import static org.xtclang.plugin.XtcPluginConstants.XTC_MAGIC;
import static org.xtclang.plugin.XtcPluginConstants.XTC_MODULE_FILE_EXTENSION;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.nio.file.Files;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import org.gradle.api.GradleException;

/**
 * XTC Plugin Helper methods in a utility class.
 * <p>
 * TODO: Move the state independent/reentrant stuff from XtcProjectDelegate to here.
 */
public final class XtcPluginUtils {

    public static final DateTimeFormatter TIMESTAMP_FORMAT = ofPattern("yyyyMMdd_HHmmss", Locale.ROOT);

    private XtcPluginUtils() {
    }

    public static List<String> argumentArrayToList(final String... args) {
        return Arrays.stream(ensureNotNull(args)).map(String::valueOf).toList();
    }

    private static Object[] ensureNotNull(final String... array) {
        Arrays.stream(array).forEach(e -> requireNonNull(e, "Arguments must never be null."));
        return array;
    }

    public static String capitalize(final String string) {
        return Character.toUpperCase(string.charAt(0)) + string.substring(1);
    }

    /**
     * Create a GradleException with a formatted message using {} placeholders.
     * This provides a cleaner alternative to string concatenation for error messages.
     *
     * <p>Example usage:
     * <pre>
     * throw failure("Failed to process file {} with size {}", file.getName(), size);
     * </pre>
     *
     * @param template The message template with {} placeholders
     * @param params   Parameters to substitute into the template
     * @return A GradleException with the formatted message
     */
    public static GradleException failure(final String template, final Object... params) {
        return failure(null, template, params);
    }

    /**
     * Create a GradleException with a formatted message and a cause.
     *
     * @param cause    The underlying cause of the exception
     * @param template The message template with {} placeholders
     * @param params   Parameters to substitute into the template
     * @return A GradleException with the formatted message and cause
     */
    public static GradleException failure(final Throwable cause, final String template, final Object... params) {
        return new GradleException("[plugin] " + formatTemplate(template, params), cause);
    }

    /**
     * Format a message template by replacing {} placeholders with provided parameters.
     * This uses the same formatting logic as the Console in javatools.
     *
     * @param template The message template with {} placeholders
     * @param params   Parameters to substitute into the template
     * @return The formatted message
     */
    private static String formatTemplate(final String template, final Object... params) {
        if (template == null || params == null || params.length == 0) {
            return template;
        }
        final var numbered = new StringBuilder(template.length() + params.length * 3);
        int paramIndex = 0;
        int pos = 0;
        while (pos < template.length()) {
            int openBrace = template.indexOf('{', pos);
            if (openBrace == -1) {
                numbered.append(template.substring(pos));
                break;
            }
            numbered.append(template, pos, openBrace);
            if (openBrace + 1 < template.length() && template.charAt(openBrace + 1) == '}') {
                numbered.append('{').append(paramIndex++).append('}');
                pos = openBrace + 2;
            } else {
                numbered.append('{');
                pos = openBrace + 1;
            }
        }
        return MessageFormat.format(numbered.toString(), params);
    }

    /**
     * Expands %TIMESTAMP% placeholder in a file path pattern to yyyyMMddHHmmss format.
     * This format matches the timestamp used throughout the XTC plugin for log files.
     *
     * @param pathPattern The path pattern that may contain %TIMESTAMP% (must not be null)
     * @return The path with timestamp placeholder expanded, or the original path if no placeholder found
     */
    public static String expandTimestampPlaceholder(final String pathPattern) {
        requireNonNull(pathPattern, "Path pattern must not be null");
        if (!pathPattern.contains("%TIMESTAMP%")) {
            return pathPattern;
        }
        final String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        return pathPattern.replace("%TIMESTAMP%", timestamp);
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
        public static boolean isValidJavaToolsArtifact(final File file, final String artifactVersion) {
            final String name = file.getName();
            final String expectedVersionedName = "javatools-" + artifactVersion + ".jar";
            
            // Check for exact name (XDK distribution) or exact versioned name (configuration resolution)
            return (XDK_JAVATOOLS_NAME_JAR.equals(name) || expectedVersionedName.equals(name))
                && hasJarExtension(file) && readXdkVersionFromJar(file) != null;
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
        
        /**
         * Safe version of isValidXtcModule for use in filters and streams.
         * Logs I/O exceptions and returns false instead of throwing.
         * 
         * @param file file to check
         * @param logger Gradle logger for error reporting
         * @return true if the file seems to be a valid XTC module, false otherwise (including I/O errors)
         */
        public static boolean isValidXtcModuleSafe(final File file, final org.gradle.api.logging.Logger logger) {
            try {
                return isValidXtcModule(file);
            } catch (final GradleException e) {
                // Log the exception and return false for safe usage in filters
                logger.warn("[plugin] Cannot validate XTC module file '{}': {}", file.getAbsolutePath(), e.getMessage(), e);
                return false;
            }
        }

        @SuppressWarnings("SameParameterValue")
        private static boolean isValidXtcModule(final File file, final boolean checkMagic) {
            if (!file.exists() || !file.isFile() || !hasFileExtension(file, XTC_MODULE_FILE_EXTENSION)) {
                return false;
            }
            if (!checkMagic) {
                return true;
            }
            try (var in = new DataInputStream(new FileInputStream(file))) {
                return (in.readInt() & 0xffff_ffffL) == XTC_MAGIC;
            } catch (final IOException e) {
                // IOException when reading magic number must be logged and propagated
                // This could happen due to file corruption, truncation, or permission issues
                throw failure(e, "Failed to read magic number from potential XTC module file: {}", file.getAbsolutePath());
            }
        }

        @SuppressWarnings("unused")
        public static File checkXtcModule(final File file) {
            return checkXtcModule(file, true);
        }

        @SuppressWarnings("SameParameterValue")
        private static File checkXtcModule(final File file, final boolean checkMagic) {
            if (!isValidXtcModule(file, checkMagic)) {
                throw failure("Processed '{}' as an XTC module, but it is not.", file.getAbsolutePath());
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
            try (var jarFile = new JarFile(file)) {
                final var m = jarFile.getManifest();
                final var implVersion = m.getMainAttributes().get(Attributes.Name.IMPLEMENTATION_VERSION);
                if (implVersion == null) {
                    throw failure("Invalid manifest entries found in '{}'", path);
                }
                return implVersion.toString();
            } catch (final IOException e) {
                throw failure(e, "Not a valid '{}': '{}'", XDK_JAVATOOLS_NAME_JAR, path);
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

        /**
         * Computes the MD5 hash of a file for verification purposes.
         *
         * @param file The file to compute MD5 hash for
         * @return The MD5 hash as a hexadecimal string, or "ERROR" if computation fails
         */
        public static String computeMd5(final File file) {
            try (final FileInputStream fis = new FileInputStream(file)) {
                final MessageDigest md = MessageDigest.getInstance("MD5");
                final byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    md.update(buffer, 0, bytesRead);
                }
                final byte[] digest = md.digest();
                final StringBuilder sb = new StringBuilder();
                for (final byte b : digest) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
            } catch (final NoSuchAlgorithmException | IOException e) {
                return "ERROR";
            }
        }

        /**
         * Creates a one-line log string with jar file metadata: path, last modified timestamp, size, and MD5 hash.
         * Uses modern Java time types (java.time.Instant) instead of deprecated java.util.Date.
         *
         * @param file The jar file to log metadata for
         * @return A formatted string containing: path, ISO-8601 timestamp, size in bytes, and MD5 hash
         */
        public static String formatJarMetadata(final File file) {
            final String path = file.getAbsolutePath();
            final Instant lastModified = Instant.ofEpochMilli(file.lastModified());
            final String timestamp = DateTimeFormatter.ISO_INSTANT.format(lastModified);
            final long size = file.length();
            final String md5 = computeMd5(file);
            return String.format("path=%s, modified=%s, size=%d bytes, md5=%s", path, timestamp, size, md5);
        }
    }
}
