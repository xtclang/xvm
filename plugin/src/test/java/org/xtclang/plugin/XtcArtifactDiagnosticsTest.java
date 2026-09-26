package org.xtclang.plugin;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.gradle.api.GradleException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class XtcArtifactDiagnosticsTest {
    @TempDir
    Path directory;

    @Test
    void missingManifestReportsTheArtifact() throws IOException {
        final var file = directory.resolve("javatools.jar");
        try (final var jar = new JarOutputStream(Files.newOutputStream(file))) {
            jar.putNextEntry(new JarEntry("data"));
            jar.closeEntry();
        }

        final var failure = assertThrows(GradleException.class,
            () -> XtcPluginUtils.FileUtils.readXdkVersionFromJar(file.toFile()));
        assertTrue(failure.getMessage().contains("manifest"));
        assertTrue(failure.getMessage().contains(file.toString()));
    }

    @Test
    void invalidJarRetainsIoCause() throws IOException {
        final var file = Files.writeString(directory.resolve("javatools.jar"), "not a ZIP");
        final var failure = assertThrows(GradleException.class,
            () -> XtcPluginUtils.FileUtils.readXdkVersionFromJar(file.toFile()));
        assertInstanceOf(IOException.class, failure.getCause());
        assertTrue(failure.getMessage().contains(file.toString()));
    }

    @Test
    void missingJarRetainsIoCause() {
        final var file = directory.resolve("missing.jar");
        final var failure = assertThrows(GradleException.class,
            () -> XtcPluginUtils.FileUtils.readXdkVersionFromJar(file.toFile()));
        assertInstanceOf(IOException.class, failure.getCause());
        assertTrue(failure.getMessage().contains(file.toString()));
    }
}
