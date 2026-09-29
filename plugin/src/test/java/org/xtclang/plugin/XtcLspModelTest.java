package org.xtclang.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import groovy.json.JsonSlurper;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercise the real plugin's evaluated model and producers without compiling XTC modules. */
class XtcLspModelTest {
    @TempDir
    Path directory;

    @Test
    void evaluatedPathsFollowProvidersAndProcessedResourcesOnCacheReuse() throws IOException {
        write("settings.gradle.kts", "rootProject.name = \"model-test\"\n");
        write("build.gradle.kts", """
            plugins { id("org.xtclang.xtc-plugin") }
            version = "1.0"
            val generated = tasks.register<Copy>("generateSources") {
                from("templates")
                into(layout.buildDirectory.dir("generated/x"))
            }
            sourceSets.main {
                xtc.setSrcDirs(listOf("custom-source"))
                xtc.srcDir(generated)
                resources.setSrcDirs(listOf("custom-resources"))
                resources.exclude("excluded.txt")
            }
            tasks.named<Copy>("processXtcResources") {
                into(layout.buildDirectory.dir("processed-assets"))
                rename("raw.txt", "data.txt")
                filter { it.uppercase() }
            }
            layout.buildDirectory.set(layout.projectDirectory.dir("relocated"))
            """);
        write("custom-source/Assets.x", "module Assets { static String text() = $./data.txt; }");
        write("templates/Generated.x", "module Generated {}");
        write("custom-resources/raw.txt", "filtered content");
        write("custom-resources/excluded.txt", "excluded");

        final var first = run("exportXtcLspModel");
        assertEquals(TaskOutcome.SUCCESS, first.task(":generateSources").getOutcome());
        assertNull(first.task(":compileXtc"));
        assertNull(first.task(":processXtcResources"));
        final var main = sourceSets().stream().filter(entry -> entry.get("sourceSet").equals("main")).findFirst().orElseThrow();
        assertEquals(List.of(uri("custom-source"), uri("relocated/generated/x")), main.get("sourceRoots"));
        assertEquals(List.of(uri("custom-resources")), main.get("resourceSourceRoots"));
        assertEquals(List.of(uri("relocated/processed-assets")), main.get("resourceRoots"));
        assertTrue(((List<?>) main.get("moduleRoots")).containsAll(List.of(uri("custom-source/Assets.x"), uri("relocated/generated/x/Generated.x"))));
        assertTrue(sourceSets().stream().allMatch(entry -> ((List<?>) entry.get("modulePath")).isEmpty()));
        assertTrue(first.getOutput().contains("Configuration cache entry stored"));
        assertTrue(run("exportXtcLspModel").getOutput().contains("Configuration cache entry reused"));

        final var prepared = run("prepareXtcLspModel");
        assertNull(prepared.task(":compileXtc"));
        assertEquals("FILTERED CONTENT", Files.readString(directory.resolve("relocated/processed-assets/data.txt")).strip());
        assertFalse(Files.exists(directory.resolve("relocated/processed-assets/excluded.txt")));
        assertTrue(run("prepareXtcLspModel").getOutput().contains("Configuration cache entry reused"));
        write("custom-resources/raw.txt", "a changed resource");
        final var changed = run("prepareXtcLspModel");
        assertTrue(changed.getOutput().contains("Configuration cache entry reused"));
        assertEquals("A CHANGED RESOURCE", Files.readString(directory.resolve("relocated/processed-assets/data.txt")).strip());
    }

    @Test
    void projectOwnershipAndDependenciesAreReplacedWithoutStaleReports() throws IOException {
        write("settings.gradle.kts", "rootProject.name = \"model-test\"\ninclude(\"library\", \"app\")\n");
        write("build.gradle.kts", "plugins { id(\"org.xtclang.xtc-plugin\") apply false }\n");
        write("library/build.gradle.kts", "plugins { id(\"org.xtclang.xtc-plugin\") }\nversion = \"1.0\"\n");
        write("app/build.gradle.kts", """
            plugins { id("org.xtclang.xtc-plugin") }
            version = "1.0"
            dependencies { add("xtcModule", project(":library")) }
            """);
        write("library/src/main/x/Library.x", "module Library {}");
        write("app/src/main/x/App.x", "module App { package lib import Library; }");
        final var result = run("exportXtcLspModel");
        assertTrue(result.getTasks().stream().noneMatch(task -> task.getPath().contains("compile")));
        final var model = sourceSets();
        assertEquals(4, model.size());
        final var library = model.stream().filter(entry -> entry.get("projectPath").equals(":library")).findFirst().orElseThrow();
        final var app = model.stream().filter(entry -> entry.get("projectPath").equals(":app") && entry.get("sourceSet").equals("main")).findFirst().orElseThrow();
        assertEquals(List.of(library.get("projectId")), app.get("projectDependencies"));
        assertTrue(((List<?>) app.get("modulePath")).isEmpty());
        write("settings.gradle.kts", "rootProject.name = \"model-test\"\ninclude(\"library\")\n");
        run("exportXtcLspModel");
        assertEquals(2, sourceSets().size());
        assertTrue(sourceSets().stream().allMatch(entry -> entry.get("projectPath").equals(":library")));
    }

    private String uri(final String path) {
        return directory.resolve(path).toFile().toURI().toString();
    }

    private void write(final String path, final String content) throws IOException {
        final var file = directory.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> sourceSets() {
        final var model = (Map<String, Object>) new JsonSlurper().parse(directory.resolve(".gradle/xtc/lsp-model.json").toFile());
        assertEquals(1, model.get("schemaVersion"));
        return (List<Map<String, Object>>) model.get("sourceSets");
    }

    private BuildResult run(final String task) {
        return GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath()
            .withArguments(task, "--configuration-cache", "--configuration-cache-problems=fail", "--no-watch-fs", "--stacktrace")
            .build();
    }
}
