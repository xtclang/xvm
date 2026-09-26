package org.xtclang.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises real plugin tasks through configuration-cache storage and reuse.
 */
class ConfigurationCacheCompatibilityTest {
    @TempDir
    Path testProjectDir;

    @Test
    void resourcesFollowLateConfigurationAndChangesOnCacheReuse() throws IOException {
        Files.writeString(testProjectDir.resolve("settings.gradle.kts"), "rootProject.name = \"cache-test\"\n");
        Files.writeString(testProjectDir.resolve("build.gradle.kts"), """
            import org.xtclang.plugin.tasks.XtcCompileTask

            plugins {
                id("org.xtclang.xtc-plugin")
            }
            version = "1.0"
            tasks.named<Copy>("processXtcResources") {
                filter { it.uppercase() }
            }.get()
            tasks.named<XtcCompileTask>("compileXtc") {
                // Keep the real producer dependency and inputs; record processed contents.
                actions.clear()
                val resources = resourceDirectory
                val destination = outputDirectory
                doLast {
                    val output = destination.get().asFile
                    output.mkdirs()
                    output.resolve("resource.txt").writeText(
                        resources.get().file("included.txt").asFile.readText())
                }
            }.get()
            layout.buildDirectory.set(layout.projectDirectory.dir("relocated-build"))
            sourceSets.main {
                resources.setSrcDirs(listOf("extra-resources"))
                resources.exclude("excluded.txt")
            }
            """);
        final var resources = Files.createDirectory(testProjectDir.resolve("extra-resources"));
        final var input = Files.writeString(resources.resolve("included.txt"), "first");
        Files.writeString(resources.resolve("excluded.txt"), "excluded");
        final var sources = Files.createDirectories(testProjectDir.resolve("src/main/x"));
        Files.writeString(sources.resolve("Example.x"), "module Example {}");
        final var destination = testProjectDir.resolve("relocated-build/xtc/main/resources");
        final var compiled = testProjectDir.resolve("relocated-build/xtc/main/lib/resource.txt");

        final var first = runBuild("compileXtc");
        assertEquals(TaskOutcome.SUCCESS, first.task(":processXtcResources").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, first.task(":compileXtc").getOutcome());
        assertTrue(first.getOutput().contains("Configuration cache entry stored"));
        assertEquals("FIRST", Files.readString(destination.resolve("included.txt")).strip());
        assertEquals("FIRST", Files.readString(compiled).strip());
        assertFalse(Files.exists(destination.resolve("excluded.txt")));

        Files.writeString(input, "second");
        final var changed = runBuild("compileXtc");
        assertTrue(changed.getOutput().contains("Configuration cache entry reused"));
        assertEquals(TaskOutcome.SUCCESS, changed.task(":processXtcResources").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, changed.task(":compileXtc").getOutcome());
        assertEquals("SECOND", Files.readString(destination.resolve("included.txt")).strip());
        assertEquals("SECOND", Files.readString(compiled).strip());

        Files.writeString(resources.resolve("excluded.txt"), "changed but excluded");
        final var unchanged = runBuild("compileXtc");
        assertTrue(unchanged.getOutput().contains("Configuration cache entry reused"));
        assertEquals(TaskOutcome.UP_TO_DATE, unchanged.task(":processXtcResources").getOutcome());
        assertEquals(TaskOutcome.UP_TO_DATE, unchanged.task(":compileXtc").getOutcome());
    }

    @Test
    void changingOnlyModuleRootsInvalidatesCompileInputs() throws IOException {
        Files.writeString(testProjectDir.resolve("settings.gradle.kts"), "rootProject.name = \"module-roots\"\n");
        Files.writeString(testProjectDir.resolve("build.gradle.kts"), """
            import org.xtclang.plugin.tasks.XtcCompileTask

            plugins {
                id("org.xtclang.xtc-plugin")
            }
            version = "1.0"
            tasks.named<XtcCompileTask>("compileXtc") {
                // Exercise the real task inputs and discovery without bootstrapping a compiler.
                // The action records what would be passed to the compiler.
                actions.clear()
                val modules = moduleSources
                val destination = outputDirectoryInternal
                doLast {
                    val output = destination.asFile
                    output.mkdirs()
                    output.resolve("modules.txt").writeText(
                        modules.files.map { it.name }.sorted().joinToString(","))
                }
            }.get()
            if (providers.gradleProperty("nestedModule").isPresent) {
                sourceSets.main {
                    xtc.srcDir("src/main/x/nested")
                }
            }
            """);
        final var nested = Files.createDirectories(testProjectDir.resolve("src/main/x/nested"));
        Files.writeString(nested.getParent().resolve("Root.x"), "module Root {}");
        Files.writeString(nested.resolve("Nested.x"), "module Nested {}");
        final var output = testProjectDir.resolve("build/xtc/main/lib/modules.txt");

        final var first = runBuild("compileXtc");
        assertEquals(TaskOutcome.SUCCESS, first.task(":compileXtc").getOutcome());
        assertEquals("Root.x", Files.readString(output));
        final var added = runBuild("compileXtc", "-PnestedModule");
        assertEquals(TaskOutcome.SUCCESS, added.task(":compileXtc").getOutcome());
        assertEquals("Nested.x,Root.x", Files.readString(output));
        final var reused = runBuild("compileXtc", "-PnestedModule");
        assertTrue(reused.getOutput().contains("Configuration cache entry reused"));
        assertEquals(TaskOutcome.UP_TO_DATE, reused.task(":compileXtc").getOutcome());
    }

    private BuildResult runBuild(final String... tasksAndOptions) {
        final var arguments = new ArrayList<>(List.of(tasksAndOptions));
        arguments.addAll(List.of("--configuration-cache", "--configuration-cache-problems=fail", "--stacktrace"));
        return GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments(arguments)
            .build();
    }
}
