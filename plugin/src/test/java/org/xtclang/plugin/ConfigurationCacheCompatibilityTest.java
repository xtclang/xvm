package org.xtclang.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Exercises real plugin tasks through configuration-cache storage and reuse.
 */
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true",
    disabledReason = "Runs TestKit Gradle builds; enable with RUN_INTEGRATION_TESTS=true")
class ConfigurationCacheCompatibilityTest {
    @TempDir
    Path testProjectDir;

    @Test
    void disablingRebuildCannotRestoreOutputsFromAnOlderCompiler() throws IOException {
        Files.writeString(testProjectDir.resolve("settings.gradle.kts"), """
            rootProject.name = "rebuild-cache"
            buildCache {
                local { directory = file("local-cache") }
            }
            """);
        Files.writeString(testProjectDir.resolve("build.gradle.kts"), """
            import org.xtclang.plugin.tasks.XtcCompileTask

            plugins {
                id("org.xtclang.xtc-plugin")
            }
            version = "1.0"
            // The fixture supplies its compiler directly in the extracted XDK directory.
            tasks.named("extractXdk") { enabled = false }
            tasks.named<XtcCompileTask>("compileXtc") {
                // Keep the real compiler inputs and cache policy; simulate the compiler's
                // timestamp shortcut without bootstrapping an XDK in this TestKit fixture.
                actions.clear()
                val compiler = layout.buildDirectory.file("xtc/xdk/lib/javatools.jar")
                val destination = outputDirectory
                val force = rebuild
                doLast {
                    val output = destination.get().file("Example.xtc").asFile
                    output.parentFile.mkdirs()
                    if (force.get() || !output.exists()) {
                        output.writeText(compiler.get().asFile.readText())
                    }
                }
            }
            """);
        final Path sources = Files.createDirectories(testProjectDir.resolve("src/main/x"));
        Files.writeString(sources.resolve("Example.x"), "module Example {}");
        final Path libraries = Files.createDirectories(testProjectDir.resolve("build/xtc/xdk/lib"));
        final Path compiler = Files.writeString(libraries.resolve("javatools.jar"), "old compiler");
        final Path output = testProjectDir.resolve("build/xtc/main/lib/Example.xtc");

        final BuildResult old = runBuild("compileXtc", "--build-cache", "-PxtcDefaultRebuild=false");
        assertEquals(TaskOutcome.SUCCESS, old.task(":compileXtc").getOutcome());
        assertEquals("old compiler", Files.readString(output));

        edit(compiler, "fixed compiler");
        final BuildResult fixed = runBuild("compileXtc", "--build-cache");
        assertEquals(TaskOutcome.SUCCESS, fixed.task(":compileXtc").getOutcome());
        assertEquals("fixed compiler", Files.readString(output));

        final BuildResult disabled = runBuild("compileXtc", "--build-cache", "-PxtcDefaultRebuild=false");
        assertEquals(TaskOutcome.SUCCESS, disabled.task(":compileXtc").getOutcome());
        assertEquals("fixed compiler", Files.readString(output));

        edit(compiler, "another compiler");
        final BuildResult unchanged = runBuild("compileXtc", "--build-cache", "-PxtcDefaultRebuild=false");
        assertTrue(unchanged.getOutput().contains("Configuration cache entry reused"));
        assertEquals(TaskOutcome.UP_TO_DATE, unchanged.task(":compileXtc").getOutcome());
        assertEquals("fixed compiler", Files.readString(output));

        Files.delete(output);
        final BuildResult missing = runBuild("compileXtc", "--build-cache", "-PxtcDefaultRebuild=false");
        assertEquals(TaskOutcome.SUCCESS, missing.task(":compileXtc").getOutcome());
        assertEquals("another compiler", Files.readString(output));

        edit(compiler, "fixed compiler");
        Files.delete(output);
        final BuildResult cached = runBuild("compileXtc", "--build-cache");
        assertEquals(TaskOutcome.FROM_CACHE, cached.task(":compileXtc").getOutcome());
        assertEquals("fixed compiler", Files.readString(output));
    }

    @ParameterizedTest
    @ValueSource(strings = {"xtc/main/resources", "custom-resources"})
    void resourcesFollowLateConfigurationAndChangesOnCacheReuse(final String resourceDestination) throws IOException {
        Files.writeString(testProjectDir.resolve("settings.gradle.kts"), "rootProject.name = \"cache-test\"\n");
        Files.writeString(testProjectDir.resolve("build.gradle.kts"), """
            import org.xtclang.plugin.tasks.XtcCompileTask

            plugins {
                id("org.xtclang.xtc-plugin")
            }
            version = "1.0"
            tasks.named<Copy>("processXtcResources") {
                into(layout.buildDirectory.dir("%s"))
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
            """.formatted(resourceDestination));
        final var resources = Files.createDirectory(testProjectDir.resolve("extra-resources"));
        final var input = Files.writeString(resources.resolve("included.txt"), "first");
        Files.writeString(resources.resolve("excluded.txt"), "excluded");
        final var sources = Files.createDirectories(testProjectDir.resolve("src/main/x"));
        Files.writeString(sources.resolve("Example.x"), "module Example {}");
        final var destination = testProjectDir.resolve("relocated-build").resolve(resourceDestination);
        final var compiled = testProjectDir.resolve("relocated-build/xtc/main/lib/resource.txt");

        final var first = runBuild("compileXtc");
        assertEquals(TaskOutcome.SUCCESS, first.task(":processXtcResources").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, first.task(":compileXtc").getOutcome());
        assertTrue(first.getOutput().contains("Configuration cache entry stored"));
        assertEquals("FIRST", Files.readString(destination.resolve("included.txt")).strip());
        assertEquals("FIRST", Files.readString(compiled).strip());
        assertFalse(Files.exists(destination.resolve("excluded.txt")));

        edit(input, "second");
        final var changed = runBuild("compileXtc");
        assertTrue(changed.getOutput().contains("Configuration cache entry reused"));
        assertEquals(TaskOutcome.SUCCESS, changed.task(":processXtcResources").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, changed.task(":compileXtc").getOutcome());
        assertEquals("SECOND", Files.readString(destination.resolve("included.txt")).strip());
        assertEquals("SECOND", Files.readString(compiled).strip());

        edit(resources.resolve("excluded.txt"), "changed but excluded");
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

    @Test
    void generatedSourceRootsComposeWithFiltersAndProducerDependencies() throws IOException {
        Files.writeString(testProjectDir.resolve("settings.gradle.kts"), "rootProject.name = \"generated-sources\"\n");
        Files.writeString(testProjectDir.resolve("build.gradle.kts"), """
            import org.xtclang.plugin.tasks.XtcCompileTask

            plugins {
                id("org.xtclang.xtc-plugin")
            }
            version = "1.0"
            val generateSources = tasks.register<Copy>("generateSources") {
                from("templates")
                into(layout.buildDirectory.dir("generated/x"))
            }
            sourceSets.main {
                xtc.srcDir(generateSources)
                xtc.exclude("Ignored.x")
            }
            // Public Gradle consumers must be able to nest the typed XTC source set.
            val combined = objects.sourceDirectorySet("combined", "combined sources")
                .source(sourceSets.main.get().xtc)
            tasks.named<XtcCompileTask>("compileXtc") {
                actions.clear()
                val modules = moduleSources
                val allSources = combined.asFileTree
                val destination = outputDirectory
                doLast {
                    val output = destination.get().asFile
                    output.mkdirs()
                    check(allSources.files.map { it.name }.sorted() == modules.files.map { it.name }.sorted())
                    output.resolve("sources.txt").writeText(allSources.singleFile.readText())
                }
            }
            """);
        final var templates = Files.createDirectory(testProjectDir.resolve("templates"));
        final var source = Files.writeString(templates.resolve("Example.x"), "module Example {}");
        Files.writeString(templates.resolve("Ignored.x"), "module Ignored {}");
        final var output = testProjectDir.resolve("build/xtc/main/lib/sources.txt");

        final var first = runBuild("compileXtc");
        assertEquals(TaskOutcome.SUCCESS, first.task(":generateSources").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, first.task(":compileXtc").getOutcome());
        assertEquals("module Example {}", Files.readString(output));
        final var reused = runBuild("compileXtc");
        assertTrue(reused.getOutput().contains("Configuration cache entry reused"));
        assertEquals(TaskOutcome.UP_TO_DATE, reused.task(":compileXtc").getOutcome());
        edit(source, "module Example { /* changed */ }");
        final var changed = runBuild("compileXtc");
        assertTrue(changed.getOutput().contains("Configuration cache entry reused"));
        assertEquals(TaskOutcome.SUCCESS, changed.task(":generateSources").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, changed.task(":compileXtc").getOutcome());
        assertEquals("module Example { /* changed */ }", Files.readString(output));
    }

    /**
     * Rewrites an input between builds without depending on the clock. Gradle reuses a file's cached hash
     * while its length and modification time are unchanged, and a rewrite within one timestamp tick keeps
     * the old time, so move the timestamp explicitly instead of waiting for the clock to advance.
     */
    private static void edit(final Path file, final String content) throws IOException {
        final var previous = Files.getLastModifiedTime(file).toInstant();
        Files.writeString(file, content);
        Files.setLastModifiedTime(file, FileTime.from(previous.plusSeconds(1)));
    }

    private BuildResult runBuild(final String... tasksAndOptions) {
        final var arguments = new ArrayList<>(List.of(tasksAndOptions));
        // Without file-system watching, every build re-reads its inputs from disk instead of trusting a
        // retained snapshot that is only corrected once an asynchronous change notification arrives.
        arguments.addAll(List.of("--configuration-cache", "--configuration-cache-problems=fail", "--no-watch-fs", "--stacktrace"));
        return GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withPluginClasspath()
            .withArguments(arguments)
            .build();
    }
}
