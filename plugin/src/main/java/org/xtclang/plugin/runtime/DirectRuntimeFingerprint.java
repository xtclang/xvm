package org.xtclang.plugin.runtime;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import org.xtclang.plugin.XtcLauncherRuntime;

import static org.xtclang.plugin.XtcPluginUtils.failure;

/**
 * Build-scoped cache key for an isolated direct runtime.
 *
 * <p>The fingerprint intentionally tracks the plugin code source as well as the
 * resolved launcher runtime entries. That prevents a reused direct-runtime entry
 * from surviving a plugin rebuild within the same build session, and it avoids
 * assuming that a stable path always means stable contents.
 */
record DirectRuntimeFingerprint(
        String source,
        String pluginCodeSource,
        List<String> classpathEntries) {

    String describeForLogging() {
        return "source=" + source
            + ", pluginCodeSource=" + pluginCodeSource
            + ", classpathEntries=" + classpathEntries;
    }

    static DirectRuntimeFingerprint from(final XtcLauncherRuntime runtime, final URL pluginCodeSource) {
        return new DirectRuntimeFingerprint(
            runtime.source(),
            pluginCodeSource.toExternalForm(),
            runtime.classpath().stream()
                .map(DirectRuntimeFingerprint::describeFile)
                .toList()
        );
    }

    private static String describeFile(final File file) {
        // Path alone is not enough here because self-hosting builds can rewrite jars in place.
        return file.getAbsolutePath() + "::" + sha256(file);
    }

    private static String sha256(final File file) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
                final byte[] buffer = new byte[8192];
                for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return toHex(digest.digest());
        } catch (final IOException | NoSuchAlgorithmException e) {
            throw failure(e, "Failed to fingerprint runtime entry: {}", file.getAbsolutePath());
        }
    }

    private static String toHex(final byte[] bytes) {
        final var builder = new StringBuilder(bytes.length * 2);
        for (final byte value : bytes) {
            builder.append(Character.forDigit((value >>> 4) & 0xF, 16));
            builder.append(Character.forDigit(value & 0xF, 16));
        }
        return builder.toString();
    }
}
