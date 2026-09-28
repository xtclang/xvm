package org.xtclang.plugin.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
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

        final var pluginUrl = createJar(tempDir.resolve("plugin.jar")).toUri().toURL();

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

        final var pluginUrl = createJar(tempDir.resolve("plugin.jar")).toUri().toURL();
        final var before = DirectRuntimeFingerprint.from(runtime, pluginUrl);

        Files.writeString(helperJar, "changed");

        final var after = DirectRuntimeFingerprint.from(runtime, pluginUrl);
        assertNotEquals(before, after);
    }

    @Test
    void changedPluginJarProducesDifferentFingerprint() throws IOException {
        final var launcher = createJar(tempDir.resolve("javatools.jar"));
        final var runtime = new XtcLauncherRuntime("test", launcher.toFile(), List.of(launcher.toFile()));
        final var plugin = Files.writeString(tempDir.resolve("plugin.jar"), "before");
        final var modified = Files.getLastModifiedTime(plugin);
        final var before = DirectRuntimeFingerprint.from(runtime, plugin.toUri().toURL());

        Files.writeString(plugin, "after!");
        Files.setLastModifiedTime(plugin, modified);

        assertNotEquals(before, DirectRuntimeFingerprint.from(runtime, plugin.toUri().toURL()));
    }

    @Test
    void changedPluginDirectoryProducesDifferentFingerprint() throws IOException {
        final var launcher = createJar(tempDir.resolve("javatools.jar"));
        final var runtime = new XtcLauncherRuntime("test", launcher.toFile(), List.of(launcher.toFile()));
        final var plugin = Files.createDirectories(tempDir.resolve("classes/org/example"));
        final var implementation = Files.writeString(plugin.resolve("Executor.class"), "before");
        final var modified = Files.getLastModifiedTime(implementation);
        final var location = tempDir.resolve("classes").toUri().toURL();
        final var before = DirectRuntimeFingerprint.from(runtime, location);

        Files.writeString(implementation, "after!");
        Files.setLastModifiedTime(implementation, modified);

        assertNotEquals(before, DirectRuntimeFingerprint.from(runtime, location));
    }

    private static Path createJar(final Path path) throws IOException {
        try (final var jarStream = new JarOutputStream(Files.newOutputStream(path))) {
            jarStream.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            jarStream.closeEntry();
        }
        return path;
    }
}
