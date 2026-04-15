package org.xtclang.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xtclang.plugin.XtcLauncherRuntime;

class DirectRuntimeFingerprintTest {
    @TempDir
    Path tempDir;

    @Test
    void sameRuntimeProducesSameFingerprint() throws IOException {
        final var launcherJar = createJar(tempDir.resolve("javatools.jar"));
        final var helperJar = createJar(tempDir.resolve("helper.jar"));
        final var runtime = new XtcLauncherRuntime(
            "test-runtime",
            launcherJar.toFile(),
            List.of(helperJar.toFile(), launcherJar.toFile())
        );

        final var pluginUrl = Path.of("/plugin.jar").toUri().toURL();

        assertEquals(
            DirectRuntimeFingerprint.from(runtime, pluginUrl),
            DirectRuntimeFingerprint.from(runtime, pluginUrl)
        );
    }

    @Test
    void changedRuntimeContentsProduceDifferentFingerprints() throws Exception {
        final var launcherJar = createJar(tempDir.resolve("javatools.jar"));
        final var helperJar = createJar(tempDir.resolve("helper.jar"));
        final var runtime = new XtcLauncherRuntime(
            "test-runtime",
            launcherJar.toFile(),
            List.of(helperJar.toFile(), launcherJar.toFile())
        );

        final var pluginUrl = Path.of("/plugin.jar").toUri().toURL();
        final var before = DirectRuntimeFingerprint.from(runtime, pluginUrl);

        Thread.sleep(5L);
        Files.writeString(helperJar, "changed");

        final var after = DirectRuntimeFingerprint.from(runtime, pluginUrl);
        assertNotEquals(before, after);
    }

    private static Path createJar(final Path path) throws IOException {
        try (OutputStream fileStream = Files.newOutputStream(path);
             JarOutputStream jarStream = new JarOutputStream(fileStream)) {
            // Empty test jar.
        }
        return path;
    }
}
