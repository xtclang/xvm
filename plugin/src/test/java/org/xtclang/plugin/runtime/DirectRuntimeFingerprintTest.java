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
    void changedCoreAndPluginContentsInvalidateReuseWithoutTimestampChanges() throws Exception {
        final var launcherJar = createJar(tempDir.resolve("javatools.jar"));
        final var runtime = new XtcLauncherRuntime("test", launcherJar.toFile(), List.of(launcherJar.toFile()));
        final var plugin = Files.writeString(tempDir.resolve("plugin.jar"), "before");
        final var core = Files.writeString(tempDir.resolve("ecstasy.xtc"), "before");
        final var pluginTime = Files.getLastModifiedTime(plugin);
        final var coreTime = Files.getLastModifiedTime(core);
        final var source = plugin.toUri().toURL();
        final var before = DirectRuntimeFingerprint.from(runtime, source, List.of(core.toFile()));

        Files.writeString(core, "after!");
        Files.setLastModifiedTime(core, coreTime);
        final var changedCore = DirectRuntimeFingerprint.from(runtime, source, List.of(core.toFile()));
        assertNotEquals(before, changedCore);

        Files.writeString(plugin, "after!");
        Files.setLastModifiedTime(plugin, pluginTime);
        assertNotEquals(changedCore, DirectRuntimeFingerprint.from(runtime, source, List.of(core.toFile())));
    }

    private static Path createJar(final Path path) throws IOException {
        try (final var jarStream = new JarOutputStream(Files.newOutputStream(path))) {
            jarStream.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            jarStream.closeEntry();
        }
        return path;
    }
}
