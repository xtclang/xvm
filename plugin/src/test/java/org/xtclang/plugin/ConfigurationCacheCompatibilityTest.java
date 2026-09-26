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

    @Test
    void testSelectionInvalidatesCachedResults() throws IOException {
        Files.writeString(testProjectDir.resolve("settings.gradle.kts"), "rootProject.name = \"test-inputs\"\n");
        Files.writeString(testProjectDir.resolve("build.gradle.kts"), """
            import org.xtclang.plugin.tasks.XtcTestTask

            plugins {
                id("org.xtclang.xtc-plugin")
            }
            version = "1.0"
            val selected = providers.gradleProperty("selected").orElse("First")
            val method = providers.gradleProperty("entry").orElse("run")
            val arguments = providers.gradleProperty("arguments").orElse("one")
            xtcTest.module {
                moduleName.set(selected)
                methodName.set(method)
                moduleArgs(arguments.map { listOf(it) })
            }
            tasks.named<XtcTestTask>("testXtc") {
                // Exercise the real task's cache inputs without launching an XDK.
                actions.clear()
                val configured = modules.map { values ->
                    values.joinToString { it.moduleName.get() + ":" +
                        it.methodName.get() + ":" + it.moduleArgs.get().joinToString() }
                }
                val cliName = cliModuleName
                val cliMethod = cliMethodName
                val cliArgs = cliModuleArgs
                val destination = outputDirectory
                doLast {
                    val output = destination.get().asFile
                    output.mkdirs()
                    output.resolve("selection.txt").writeText(configured.get() + "|" +
                        cliName.orNull + "|" + cliMethod.orNull + "|" + cliArgs.getOrElse(emptyList()).joinToString())
                }
            }
            """);
        Files.createDirectories(testProjectDir.resolve("build/xtc/xdk/lib"));
        final var output = testProjectDir.resolve("build/xunit/selection.txt");
        final var selections = List.of(
            List.of("testXtc"),
            List.of("testXtc", "-Pselected=Second"),
            List.of("testXtc", "-Pselected=Second", "-Pentry=verify"),
            List.of("testXtc", "-Pselected=Second", "-Pentry=verify", "-Parguments=two"),
            List.of("testXtc", "--module=Cli"),
            List.of("testXtc", "--module=Cli", "--method=verify"),
            List.of("testXtc", "--module=Cli", "--method=verify", "--args=three,four")
        );
        final var expected = List.of(
            "First:run:one|null|null|",
            "Second:run:one|null|null|",
            "Second:verify:one|null|null|",
            "Second:verify:two|null|null|",
            "First:run:one|Cli|null|",
            "First:run:one|Cli|verify|",
            "First:run:one|Cli|verify|three, four"
        );
        for (int i = 0; i < selections.size(); i++) {
            final var result = runBuild(selections.get(i).toArray(String[]::new));
            assertEquals(TaskOutcome.SUCCESS, result.task(":testXtc").getOutcome(), selections.get(i).toString());
            assertEquals(expected.get(i), Files.readString(output));
        }
        final var reused = runBuild(selections.getLast().toArray(String[]::new));
        assertTrue(reused.getOutput().contains("Configuration cache entry reused"));
        assertEquals(TaskOutcome.UP_TO_DATE, reused.task(":testXtc").getOutcome());
    }

    @Test
    void lateSourceSetTasksExecuteWithConfigurationCache() throws IOException {
        Files.writeString(testProjectDir.resolve("settings.gradle.kts"), "rootProject.name = \"late-source-set\"\n");
        Files.writeString(testProjectDir.resolve("build.gradle.kts"), """
            import org.xtclang.plugin.tasks.XtcCompileTask
            import org.xtclang.plugin.tasks.XtcRunTask

            plugins {
                id("org.xtclang.xtc-plugin")
            }
            version = "1.0"
            tasks.named<XtcRunTask>("runXtc").get()
            sourceSets.create("extra")
            dependencies.add("xtcModuleExtra", files("dependency.xtc"))
            tasks.named<XtcCompileTask>("compileExtraXtc") {
                actions.clear()
                val selected = moduleSources
                val destination = outputDirectory
                doLast {
                    val output = destination.get().asFile
                    output.mkdirs()
                    output.resolve("modules.txt").writeText(selected.files.single().name)
                }
            }
            tasks.named<XtcRunTask>("runXtc") {
                actions.clear()
                val names = sourceSetNames
                val dependencies = xtcModuleDependencies
                val destination = layout.buildDirectory.file("observed.txt")
                outputs.file(destination)
                doLast {
                    destination.get().asFile.writeText(names.joinToString() + "|" +
                        dependencies.files.map { it.name }.sorted().joinToString())
                }
            }
            """);
        Files.createDirectories(testProjectDir.resolve("build/xtc/xdk/lib"));
        Files.writeString(testProjectDir.resolve("dependency.xtc"), "dependency");
        final var sources = Files.createDirectories(testProjectDir.resolve("src/extra/x"));
        Files.writeString(sources.resolve("Example.x"), "module Example {}");
        final var resources = Files.createDirectories(testProjectDir.resolve("src/extra/resources"));
        Files.writeString(resources.resolve("included.txt"), "resource");

        final var first = runBuild("runXtc");
        assertEquals(TaskOutcome.SUCCESS, first.task(":compileExtraXtc").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, first.task(":processExtraXtcResources").getOutcome());
        assertTrue(Files.readString(testProjectDir.resolve("build/observed.txt")).contains("extra"));
        assertTrue(Files.readString(testProjectDir.resolve("build/observed.txt")).contains("dependency.xtc"));
        assertEquals("Example.x", Files.readString(testProjectDir.resolve("build/xtc/extra/lib/modules.txt")));
        final var reused = runBuild("runXtc");
        assertTrue(reused.getOutput().contains("Configuration cache entry reused"));
        assertEquals(TaskOutcome.UP_TO_DATE, reused.task(":compileExtraXtc").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, reused.task(":runXtc").getOutcome());
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
