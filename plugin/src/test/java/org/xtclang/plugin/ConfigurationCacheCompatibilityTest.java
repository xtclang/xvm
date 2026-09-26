package org.xtclang.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        Files.writeString(testProjectDir.resolve("settings.gradle"), "rootProject.name = 'cache-test'\n");
        Files.writeString(testProjectDir.resolve("build.gradle"), """
            plugins {
                id 'org.xtclang.xtc-plugin'
            }
            version = '1.0'
            tasks.named('processXtcResources').get()
            tasks.named('compileXtc').get()
            layout.buildDirectory = layout.projectDirectory.dir('relocated-build')
            sourceSets.main.resources.setSrcDirs(['extra-resources'])
            sourceSets.main.resources.exclude('excluded.txt')
            """);
        final var resources = Files.createDirectory(testProjectDir.resolve("extra-resources"));
        final var input = Files.writeString(resources.resolve("included.txt"), "first");
        Files.writeString(resources.resolve("excluded.txt"), "excluded");
        final var destination = testProjectDir.resolve("relocated-build/xtc/main/resources");

        final var first = runResources();
        assertEquals(TaskOutcome.SUCCESS, first.task(":processXtcResources").getOutcome());
        assertTrue(first.getOutput().contains("Configuration cache entry stored"));
        assertEquals("first", Files.readString(destination.resolve("included.txt")));
        assertFalse(Files.exists(destination.resolve("excluded.txt")));

        Files.writeString(input, "second");
        final var changed = runResources();
        assertTrue(changed.getOutput().contains("Configuration cache entry reused"));
        assertEquals(TaskOutcome.SUCCESS, changed.task(":processXtcResources").getOutcome());
        assertEquals("second", Files.readString(destination.resolve("included.txt")));

        final var unchanged = runResources();
        assertTrue(unchanged.getOutput().contains("Configuration cache entry reused"));
        assertEquals(TaskOutcome.UP_TO_DATE, unchanged.task(":processXtcResources").getOutcome());
    }

    private BuildResult runResources() {
        return GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments("processXtcResources", "--configuration-cache", "--configuration-cache-problems=fail", "--stacktrace")
            .build();
    }
}
