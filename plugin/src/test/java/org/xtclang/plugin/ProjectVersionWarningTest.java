package org.xtclang.plugin;

import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * Checks which projects the plugin warns about for having no version: a project that applies the plugin and has no
 * version once its build script has run, and not the project that Gradle creates to generate the accessors of a
 * precompiled script plugin that applies this plugin (xvm#767).
 */
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true",
    disabledReason = "Runs TestKit Gradle builds; enable with RUN_INTEGRATION_TESTS=true")
class ProjectVersionWarningTest {
    private static final String WARNING = "has unspecified version";

    @TempDir
    Path testProjectDir;

    @Test
    void aProjectWithoutAVersionGetsTheWarning() throws IOException {
        Files.writeString(testProjectDir.resolve("settings.gradle.kts"), "rootProject.name = \"unversioned\"\n");
        Files.writeString(testProjectDir.resolve("build.gradle.kts"), """
            plugins {
                id("org.xtclang.xtc-plugin")
            }
            """);

        final String output = runBuild(true).getOutput();
        assertTrue(output.contains("[plugin] WARNING: Project 'unversioned' " + WARNING + '.'), output);
    }

    @Test
    void aVersionSetAfterThePluginIsAppliedGetsNoWarning() throws IOException {
        Files.writeString(testProjectDir.resolve("settings.gradle.kts"), "rootProject.name = \"versioned\"\n");
        Files.writeString(testProjectDir.resolve("build.gradle.kts"), """
            plugins {
                id("org.xtclang.xtc-plugin")
            }
            version = "1.0"
            """);

        final String output = runBuild(true).getOutput();
        assertFalse(output.contains(WARNING), output);
    }

    @Test
    void aPrecompiledScriptPluginThatAppliesThePluginGetsNoWarning() throws IOException {
        // the convention plugin applies this plugin in its plugins block, as xtclang/examples' build-logic does, so
        // that buildSrc generates its accessors for a project that Gradle creates for them, which has no version
        final Path buildSrc = Files.createDirectories(testProjectDir.resolve("buildSrc"));
        Files.writeString(buildSrc.resolve("build.gradle.kts"), """
            plugins {
                `kotlin-dsl`
            }
            repositories {
                gradlePluginPortal()
            }
            dependencies {
                implementation(files(%s))
            }
            """.formatted(pluginClasspath()));
        final Path scripts = Files.createDirectories(buildSrc.resolve("src/main/kotlin"));
        Files.writeString(scripts.resolve("xtc-conventions.gradle.kts"), """
            plugins {
                id("org.xtclang.xtc-plugin")
            }
            """);
        Files.writeString(testProjectDir.resolve("settings.gradle.kts"), "rootProject.name = \"conventions\"\n");
        Files.writeString(testProjectDir.resolve("build.gradle.kts"), """
            plugins {
                id("xtc-conventions")
            }
            version = "1.0"
            """);

        // buildSrc's classpath has the plugin, so the build under test gets no plugin classpath of its own
        final BuildResult result = runBuild(false);
        assertTrue(result.getOutput().contains("generatePrecompiledScriptPluginAccessors"), result.getOutput());
        assertFalse(result.getOutput().contains(WARNING), result.getOutput());
    }

    /**
     * Returns the plugin's implementation classpath, which TestKit's plugin classpath injection reads from the same
     * metadata, as Kotlin string literals for buildSrc's {@code files(...)}.
     */
    private static String pluginClasspath() throws IOException {
        final Properties metadata = new Properties();
        try (InputStream in = ProjectVersionWarningTest.class.getResourceAsStream("/plugin-under-test-metadata.properties")) {
            assertNotNull(in, "plugin-under-test-metadata.properties is not on the test classpath");
            metadata.load(in);
        }
        return Arrays.stream(metadata.getProperty("implementation-classpath").split(File.pathSeparator))
            .map(path -> '"' + path.replace('\\', '/') + '"')
            .collect(joining(", "));
    }

    private BuildResult runBuild(final boolean withPluginClasspath) {
        final GradleRunner runner = GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("help", "--stacktrace");
        // the build gives the nested build a TestKit home that outlives the test, so its kotlin-dsl is downloaded once
        final String testKitDir = System.getProperty("xtc.testkit.dir");
        if (testKitDir != null) {
            runner.withTestKitDir(new File(testKitDir));
        }
        return (withPluginClasspath ? runner.withPluginClasspath() : runner).build();
    }
}
