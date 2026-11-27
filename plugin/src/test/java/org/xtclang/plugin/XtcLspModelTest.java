package org.xtclang.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import groovy.json.JsonSlurper;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testkit.runner.UnexpectedBuildFailure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xtclang.plugin.internal.XtcLauncherDefaults;

/** Exercise the real plugin's evaluated model and producers without compiling XTC modules. */
class XtcLspModelTest {
    @TempDir
    Path directory;

    @Test
    void evaluatedPathsFollowProvidersAndProcessedResourcesOnCacheReuse() throws IOException {
        directory = directory.toRealPath();
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
    void exportsReplaceTheirReportsWholeInsteadOfRewritingThemInPlace() throws IOException {
        write("settings.gradle.kts", "rootProject.name = \"model-test\"\n");
        write("build.gradle.kts", "plugins { id(\"org.xtclang.xtc-plugin\") }\nversion = \"1.0\"\n");
        write("src/main/x/App.x", "module App {}");
        run("exportXtcLspModel");
        final var report = directory.resolve("build/xtc/lsp/main.json");
        final var model = directory.resolve(".gradle/xtc/lsp-model.json");
        final var reportBefore = fileKey(report);
        final var modelBefore = fileKey(model);
        assumeTrue(reportBefore != null && modelBefore != null, "the file system gives a file a key, such as its inode");

        // a new source file changes the export's inputs, so both tasks write again
        write("src/main/x/Other.x", "module Other {}");
        run("exportXtcLspModel");
        // a file moved into place is another file; one rewritten in place, which a reader can find part written, is not
        assertNotEquals(reportBefore, fileKey(report), "the source set's report is replaced whole");
        assertNotEquals(modelBefore, fileKey(model), "the model is replaced whole");
        assertTrue(sourceSets().stream().anyMatch(entry -> entry.get("moduleRoots").toString().contains("Other.x")));
        for (final var folder : List.of(report.getParent(), model.getParent())) {
            try (var files = Files.list(folder)) {
                assertEquals(List.of(), files.map(file -> file.getFileName().toString())
                    .filter(name -> name.startsWith("model-")).toList(), "no temporary file is left in " + folder);
            }
        }
    }

    private static Object fileKey(final Path file) throws IOException {
        return Files.readAttributes(file, BasicFileAttributes.class).fileKey();
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
        // in a build of its own, a project's path in the build tree is its path
        assertEquals(":library", library.get("buildTreePath"));
        assertEquals(":app", app.get("buildTreePath"));
        write("settings.gradle.kts", "rootProject.name = \"model-test\"\ninclude(\"library\")\n");
        run("exportXtcLspModel");
        assertEquals(2, sourceSets().size());
        assertTrue(sourceSets().stream().allMatch(entry -> entry.get("projectPath").equals(":library")));
    }

    @Test
    void runSettingsAreTheExtensionsAndTheExportRealizesNeitherTask() throws IOException {
        write("settings.gradle.kts", "rootProject.name = \"model-test\"\ninclude(\"app\", \"plain\")\n");
        write("build.gradle.kts", "plugins { id(\"org.xtclang.xtc-plugin\") apply false }\n");
        write("plain/build.gradle.kts", "plugins { id(\"org.xtclang.xtc-plugin\") }\nversion = \"1.0\"\n");
        final var app = """
            plugins { id("org.xtclang.xtc-plugin") }
            version = "1.0"
            xtcRun {
                jvmArgs.set(listOf("-Dxvm.origin=extension", "%s"))
                module {
                    moduleName = "App"
                    methodName = "greet"
                    moduleArgs("hello world", "%s")
                }
                module {
                    moduleName = "App"
                    moduleArgs("second")
                }
                module { moduleArgs("nameless") }
                module { moduleName = "" }
                module {
                    moduleName = "Blank"
                    methodName = ""
                }
                moduleName("Other")
            }
            xtcTest {
                jvmArgs.set(listOf("-Dxvm.origin=test"))
            }
            // a task's own configuration, which embedded runs do not see, must not even run for the export
            tasks.named("runXtc") { throw GradleException("runXtc was configured") }
            tasks.named("testXtc") { throw GradleException("testXtc was configured") }
            """;
        write("app/build.gradle.kts", app.formatted("-Xss4m", "ü"));
        write("app/src/main/x/App.x", "module App {}");
        write("app/src/test/x/AppTest.x", "module AppTest {}");

        final var first = run("exportXtcLspModel");
        assertTrue(first.getTasks().stream().noneMatch(task -> task.getPath().endsWith("runXtc") || task.getPath().endsWith("testXtc")));
        assertFalse(first.getOutput().contains("was configured"), first.getOutput());
        final var plain = entry(":plain", "main");
        final var defaults = jvmArgs(plain, "test");
        assertTrue(defaults.contains("-ea"), defaults.toString());
        assertEquals(Map.of("jvmArgs", defaults, "modules", Map.of(), "problems", List.of()), plain.get("run"));

        // the first of a module's runs is the one it has; a blank name is no module, and a blank method the default
        final var main = entry(":app", "main");
        final var run = (Map<?, ?>) main.get("run");
        // the JVM arguments that runXtc's JVM gets: the plugin's own options and -ea before the extension's jvmArgs
        assertEquals(XtcLauncherDefaults.allJvmArgs(true, List.of("-Dxvm.origin=extension", "-Xss4m")), run.get("jvmArgs"));
        assertEquals(Map.of(
            "App", Map.of("methodName", "greet", "moduleArgs", List.of("hello world", "ü")),
            "Blank", Map.of("methodName", "run", "moduleArgs", List.of()),
            "Other", Map.of("methodName", "run", "moduleArgs", List.of())), run.get("modules"));
        assertEquals(List.of("App", "Blank", "Other"), List.copyOf(((Map<?, ?>) run.get("modules")).keySet()));
        assertEquals(List.of(), run.get("problems"));
        assertEquals(Map.of("jvmArgs", XtcLauncherDefaults.allJvmArgs(true, List.of("-Dxvm.origin=test")), "problems", List.of()),
            main.get("test"));
        // each source set of the project has the project's settings
        final var tests = entry(":app", "test");
        assertEquals(main.get("run"), tests.get("run"));
        assertEquals(main.get("test"), tests.get("test"));

        assertTrue(run("exportXtcLspModel").getOutput().contains("Configuration cache entry reused"));
        write("app/build.gradle.kts", app.formatted("-Xss8m", "changed"));
        final var changed = run("exportXtcLspModel");
        assertTrue(changed.getOutput().contains("Configuration cache entry stored"));
        final var rerun = (Map<?, ?>) entry(":app", "main").get("run");
        assertEquals(XtcLauncherDefaults.allJvmArgs(true, List.of("-Dxvm.origin=extension", "-Xss8m")), rerun.get("jvmArgs"));
        assertEquals(List.of("hello world", "changed"), ((Map<?, ?>) ((Map<?, ?>) rerun.get("modules")).get("App")).get("moduleArgs"));
    }

    @Test
    void runSettingsThatCannotBeReadAreLeftOutAndNamedWithoutFailingTheExport() throws IOException {
        write("settings.gradle.kts", "rootProject.name = \"model-test\"\n");
        write("build.gradle.kts", """
            plugins { id("org.xtclang.xtc-plugin") }
            version = "1.0"
            xtcRun {
                module {
                    moduleName = "App"
                    moduleArg(providers.gradleProperty("greeting"))
                }
                module {
                    moduleName = "Thrown"
                    moduleArg(provider<String> { error("no greeting here") })
                }
                moduleName("Other")
            }
            xtcTest {
                jvmArgs(provider<List<String>> { error("no test options here") })
            }
            """);
        write("src/main/x/App.x", "module App {}");

        run("exportXtcLspModel");
        final var main = entry(":", "main");
        final var run = (Map<?, ?>) main.get("run");
        assertEquals(Map.of("Other", Map.of("methodName", "run", "moduleArgs", List.of())), run.get("modules"));
        final var problems = (List<?>) run.get("problems");
        assertEquals(2, problems.size(), problems.toString());
        assertEquals("module App's moduleArgs: it has no value", problems.getFirst());
        assertTrue(problems.getLast().toString().startsWith("module Thrown's moduleArgs: "), problems.toString());
        final var test = (Map<?, ?>) main.get("test");
        assertEquals(List.of(), test.get("jvmArgs"));
        assertTrue(((List<?>) test.get("problems")).getFirst().toString().startsWith("jvmArgs: "), test.toString());

        // once the build gives the value, the module has it
        run("exportXtcLspModel", "-Pgreeting=hi");
        final var given = (Map<?, ?>) entry(":", "main").get("run");
        assertEquals(Map.of("methodName", "run", "moduleArgs", List.of("hi")), ((Map<?, ?>) given.get("modules")).get("App"));
        assertEquals(1, ((List<?>) given.get("problems")).size(), given.toString());
    }

    private Map<String, Object> entry(final String project, final String sourceSet) {
        return sourceSets().stream().filter(entry -> entry.get("projectPath").equals(project) && entry.get("sourceSet").equals(sourceSet))
            .findFirst().orElseThrow();
    }

    /** The JVM options that {@code entry}'s {@code task}, {@code run} or {@code test}, has. */
    private static List<?> jvmArgs(final Map<String, Object> entry, final String task) {
        return (List<?>) ((Map<?, ?>) entry.get(task)).get("jvmArgs");
    }

    private String uri(final String path) {
        return directory.resolve(path).toFile().toURI().toString();
    }

    @Test
    void compositeImportAggregatesNestedBuildsAndRetainsSnapshotOnFailure() throws IOException {
        directory = directory.toRealPath();
        write("settings.gradle.kts", "rootProject.name = \"root\"\nincludeBuild(\"library\")\nincludeBuild(\"application\")\n");
        write("build.gradle.kts", "");
        write("library/settings.gradle.kts", "rootProject.name = \"library\"\nincludeBuild(\"nested\")\n");
        write("library/nested/settings.gradle.kts", "rootProject.name = \"nested\"\n");
        write("application/settings.gradle.kts", "rootProject.name = \"application\"\nincludeBuild(\"../library\")\n");
        for (final var build : List.of("library", "library/nested", "application")) {
            write(build + "/build.gradle.kts", """
                plugins { id("org.xtclang.xtc-plugin") }
                group = "sample"
                version = "1.0"
                """);
        }
        write("library/src/main/x/Library.x", "module Library {}");
        write("library/nested/src/main/x/Nested.x", "module Nested {}");
        write("application/src/main/x/App.x", "module App {}");
        Files.writeString(directory.resolve("application/build.gradle.kts"), """
            plugins { id("org.xtclang.xtc-plugin") }
            group = "sample"
            version = "1.0"
            dependencies { add("xtcModule", "sample:library:1.0") }
            xtcRun {
                module {
                    moduleName = "App"
                    moduleArgs("from-gradle")
                }
            }
            """);
        try (var script = getClass().getResourceAsStream("/compiler-model.init.gradle")) {
            Files.copy(script, directory.resolve("compiler-model.init.gradle"));
        }
        final var first = runWorkspace("first-import");
        assertTrue(first.getTasks().stream().noneMatch(task -> task.getPath().contains("compileXtc")));
        final var report = directory.resolve(".gradle/xtc/lsp-workspace.json");
        final var model = (Map<?, ?>) new JsonSlurper().parse(report.toFile());
        assertEquals("first-import", model.get("importId"));
        assertEquals(6, ((List<?>) model.get("sourceSets")).size());
        assertEquals(4, ((List<?>) model.get("buildRoots")).size());
        final var application = ((List<?>) model.get("sourceSets")).stream().map(value -> (Map<?, ?>) value)
            .filter(entry -> entry.get("projectId").equals(uri("application") + "#:"))
            .filter(entry -> entry.get("sourceSet").equals("main")).findFirst().orElseThrow();
        assertEquals(List.of(uri("library") + "#:"), application.get("projectDependencies"));
        // an included build's project is named from the composite's root, where a bare task name reaches no included build
        assertEquals(":application", application.get("buildTreePath"));
        // and its run settings come through the workspace's model as the build's own model has them
        assertEquals(Map.of("App", Map.of("methodName", "run", "moduleArgs", List.of("from-gradle"))),
            ((Map<?, ?>) application.get("run")).get("modules"));
        final var library = ((List<?>) model.get("sourceSets")).stream().map(value -> (Map<?, ?>) value)
            .filter(entry -> entry.get("projectId").equals(uri("library") + "#:"))
            .filter(entry -> entry.get("sourceSet").equals("main")).findFirst().orElseThrow();
        assertEquals(":library", library.get("buildTreePath"));
        assertTrue(runWorkspace("second-import").getOutput().contains("Configuration cache entry reused"));
        final var accepted = Files.readString(report);
        assertEquals("second-import", ((Map<?, ?>) new JsonSlurper().parseText(accepted)).get("importId"));
        write("library/nested/build.gradle.kts", "error(\"Deliberately broken nested build\")\n");
        assertThrows(UnexpectedBuildFailure.class, this::runWorkspace);
        assertEquals(accepted, Files.readString(report));
        write("settings.gradle.kts", "rootProject.name = \"root\"\nincludeBuild(\"application\")\n");
        write("application/settings.gradle.kts", "rootProject.name = \"application\"\n");
        write("application/build.gradle.kts", "plugins { id(\"org.xtclang.xtc-plugin\") }\nversion = \"1.0\"\n");
        runWorkspace();
        assertEquals(2, ((List<?>) ((Map<?, ?>) new JsonSlurper().parse(report.toFile())).get("sourceSets")).size());
    }

    private BuildResult runWorkspace() {
        return runWorkspace("");
    }

    private BuildResult runWorkspace(final String importId) {
        return GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath()
            .withEnvironment(Stream.concat(System.getenv().entrySet().stream(), Stream.of(Map.entry("XTC_COMPILER_IMPORT_ID", importId)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (previous, replacement) -> replacement)))
            .withArguments("--init-script", "compiler-model.init.gradle", "exportEcstasyWorkspaceModel",
                "--configuration-cache", "--configuration-cache-problems=fail", "--no-watch-fs", "--stacktrace")
            .build();
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

    private BuildResult run(final String task, final String... properties) {
        final var arguments = Stream.concat(Stream.of(task, "--configuration-cache", "--configuration-cache-problems=fail",
            "--no-watch-fs", "--stacktrace"), Stream.of(properties)).toList();
        return GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath().withArguments(arguments).build();
    }
}
