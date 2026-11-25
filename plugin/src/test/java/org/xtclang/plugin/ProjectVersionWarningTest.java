package org.xtclang.plugin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

/**
 * Checks which projects the plugin warns about for having no version: a project that applies the plugin and has no
 * version once its build script has run. The plugin checks only then, so a version set after it is applied counts, and
 * a project that Gradle creates without running a build script for it, as for the accessors of a precompiled script
 * plugin that applies this plugin (xvm#767), is never checked.
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

        final String output = runBuild().getOutput();
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

        final String output = runBuild().getOutput();
        assertFalse(output.contains(WARNING), output);
    }

    private BuildResult runBuild() {
        return GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments("help", "--stacktrace")
            .withPluginClasspath()
            .build();
    }
}
