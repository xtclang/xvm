package org.xtclang.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import org.gradle.testfixtures.ProjectBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class XtcLauncherRuntimeTest {

    @TempDir
    Path tempDir;

    @Test
    void resolveRuntimePrefersConfigurationClasspathWhenAvailable() throws IOException {
        final var project = ProjectBuilder.builder().withName("runtimeFromConfig").build();
        final var configDir = Files.createDirectory(tempDir.resolve("config"));
        final var xdkDir = Files.createDirectory(tempDir.resolve("xdk"));

        final var configLauncher = createLauncherJar(configDir.resolve("javatools-1.2.3.jar"), "1.2.3");
        final var configDependency = createJar(configDir.resolve("gson.jar"));
        createLauncherJar(xdkDir.resolve("javatools.jar"), "1.2.3");
        createJar(xdkDir.resolve("commons-cli.jar"));

        final var runtime = XtcJavaToolsRuntime.resolveRuntime(
            project.provider(() -> "1.2.3"),
            project.provider(() -> project.files(configLauncher, configDependency)),
            project.provider(() -> project.fileTree(xdkDir)),
            project.getLogger()
        );

        assertEquals("xdkJavaTools configuration", runtime.source());
        assertEquals(configLauncher.toFile(), runtime.launcherJar());
        assertIterableEquals(sortedFiles(configLauncher, configDependency), runtime.classpath());
    }

    @Test
    void resolveRuntimeFallsBackToExtractedXdkClasspath() throws IOException {
        final var project = ProjectBuilder.builder().withName("runtimeFromXdk").build();
        final var configDir = Files.createDirectory(tempDir.resolve("config-fallback"));
        final var xdkDir = Files.createDirectory(tempDir.resolve("xdk-fallback"));

        final var strayDependency = createJar(configDir.resolve("gson.jar"));
        final var xdkLauncher = createLauncherJar(xdkDir.resolve("javatools.jar"), "1.2.3");
        final var xdkDependency = createJar(xdkDir.resolve("commons-cli.jar"));

        final var runtime = XtcJavaToolsRuntime.resolveRuntime(
            project.provider(() -> "1.2.3"),
            project.provider(() -> project.files(strayDependency)),
            project.provider(() -> project.fileTree(xdkDir)),
            project.getLogger()
        );

        assertEquals("extracted XDK contents", runtime.source());
        assertEquals(xdkLauncher.toFile(), runtime.launcherJar());
        assertIterableEquals(sortedFiles(xdkDependency, xdkLauncher), runtime.classpath());
    }

    private List<java.io.File> sortedFiles(final Path... files) {
        return List.of(files).stream()
            .map(Path::toFile)
            .sorted(Comparator.comparing(java.io.File::getAbsolutePath))
            .toList();
    }

    private static Path createLauncherJar(final Path path, final String implementationVersion) throws IOException {
        final var manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.IMPLEMENTATION_VERSION, implementationVersion);
        return createJar(path, manifest);
    }

    private static Path createJar(final Path path) throws IOException {
        return createJar(path, null);
    }

    private static Path createJar(final Path path, final Manifest manifest) throws IOException {
        try (OutputStream fileStream = Files.newOutputStream(path);
             JarOutputStream jarStream = manifest == null
                 ? new JarOutputStream(fileStream)
                 : new JarOutputStream(fileStream, manifest)) {
            // Empty test jar.
        }
        return path;
    }
}
