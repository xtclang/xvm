package org.xtclang.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.sun.net.httpserver.HttpServer;

import groovy.json.JsonSlurper;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.gradle.testkit.runner.UnexpectedBuildFailure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.xtclang.plugin.internal.XtcLauncherDefaults;
import org.xtclang.plugin.tasks.XtcLspSourceSetTask;

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

    @Test
    void theModelSaysWhatTheXdkOfEachProjectHoldsWithoutExtractingItOrFailingOverIt() throws IOException {
        directory = directory.toRealPath();
        // XDK distributions in a repository, as org.xtclang:xdk is published: one with two modules and its javatools, one
        // with one module, and one whose archive is missing, as when a download fails after the metadata resolved
        final var javatools = jar("org/xvm/Compiler.class", "a compiler", "build-info.properties", "git.commit=82488d09\nxdk.version=9.9.9\n");
        final var released = xdk("9.9.9", Map.of("xdk/lib/ecstasy.xtc", bytes("the library"),
            "xdk/javatools/javatools_turtle.xtc", bytes("the turtle"), "xdk/javatools/javatools.jar", javatools, "xdk/bin/xec", bytes("a launcher")));
        xdk("9.9.8", Map.of("xdk/lib/ecstasy.xtc", bytes("the library")));
        pom("8.0.0");
        final var projects = List.of("released", "unfetched", "unresolved", "local", "built", "filed", "plain");
        write("settings.gradle.kts", "rootProject.name = \"model-test\"\ninclude(" + projects.stream()
            .map(name -> '"' + name + '"').collect(Collectors.joining(", ")) + ")\n");
        write("build.gradle.kts", "plugins { id(\"org.xtclang.xtc-plugin\") apply false }\n");
        for (final var asked : Map.of("released", "9.9.9", "unfetched", "8.0.0", "unresolved", "7.7.7").entrySet()) {
            write(asked.getKey() + "/build.gradle.kts", repositoryXdk(asked.getValue()));
        }
        // an XDK that a project of the build makes, as a composite build's included XDK is
        write("built/build.gradle.kts", """
            plugins { base }
            version = "8.8.8"
            val archive = tasks.register<Zip>("xdkArchive") {
                from("content")
                destinationDirectory.set(layout.buildDirectory.dir("distributions"))
            }
            configurations.consumable("xdkArchive")
            artifacts { add("xdkArchive", archive) }
            """);
        write("built/content/lib/ecstasy.xtc", "another library");
        write("local/build.gradle.kts", """
            plugins { id("org.xtclang.xtc-plugin") }
            version = "1.0"
            dependencies { add("xdkDistribution", project(path = ":built", configuration = "xdkArchive")) }
            """);
        // an XDK of a file dependency, whose archive is there, but which names no version
        write("filed/build.gradle.kts", """
            plugins { id("org.xtclang.xtc-plugin") }
            version = "1.0"
            dependencies { add("xdkDistribution", files("%s")) }
            """.formatted(released));
        write("plain/build.gradle.kts", "plugins { id(\"org.xtclang.xtc-plugin\") }\nversion = \"1.0\"\n");
        for (final var project : projects) {
            if (!project.equals("built")) {
                write(project + "/src/main/x/" + project + ".x", "module " + project + " {}");
            }
        }

        final var first = run("exportXtcLspModel");
        // the released XDK's distribution is read, not extracted: what each of its modules and its javatools are
        assertTrue(first.getTasks().stream().noneMatch(task -> task.getPath().endsWith("extractXdk")), first.getTasks().toString());
        assertFalse(Files.exists(directory.resolve("released/build/xtc/xdk/lib")));
        final var contents = Map.of("modules", Stream.of("the library", "the turtle").map(XtcLspModelTest::sha256).sorted().toList(),
            "javatools", XtcLspSourceSetTask.javatoolsRevision(javatools));
        final var expected = new HashMap<String, Object>(contents);
        expected.putAll(Map.of("version", "9.9.9", "libraries", folder("released/build/xtc/xdk/lib")));
        assertEquals(expected, entry(":released", "main").get("xdk"));
        // each source set of the project has the project's XDK
        assertEquals(expected, entry(":released", "test").get("xdk"));
        // one that cannot be fetched, or does not resolve, is named with why, and fails no export
        assertEquals(Map.of("version", "8.0.0", "libraries", folder("unfetched/build/xtc/xdk/lib"),
            "problem", "the import could not fetch its distribution"), entry(":unfetched", "main").get("xdk"));
        final var unresolved = (Map<?, ?>) entry(":unresolved", "main").get("xdk");
        assertEquals("7.7.7", unresolved.get("version"));
        assertTrue(unresolved.get("problem").toString().startsWith("the import could not resolve it (Could not find org.xtclang:xdk:7.7.7"),
            unresolved.toString());
        // the built one is named with its project, but neither built nor read for it
        assertNull(first.task(":built:xdkArchive"));
        assertEquals(Map.of("version", "8.8.8", "libraries", folder("local/build/xtc/xdk/lib"), "project", ":built"),
            entry(":local", "main").get("xdk"));
        // a file dependency's archive is read as a repository's is, without a version
        final var filed = new HashMap<String, Object>(contents);
        filed.put("libraries", folder("filed/build/xtc/xdk/lib"));
        assertEquals(filed, entry(":filed", "main").get("xdk"));
        // and a project without an XDK dependency names none
        assertFalse(entry(":plain", "main").containsKey("xdk"));
        // while an XDK cannot be fetched or resolved, each import configures the build again, to resolve it anew
        assertTrue(run("exportXtcLspModel").getOutput().contains("xdk-retry"));
        // without those, an import reuses the configuration cache's entry
        write("settings.gradle.kts", "rootProject.name = \"model-test\"\ninclude(" + projects.stream()
            .filter(name -> !name.startsWith("un")).map(name -> '"' + name + '"').collect(Collectors.joining(", ")) + ")\n");
        assertTrue(run("exportXtcLspModel").getOutput().contains("Configuration cache entry stored"));
        assertTrue(run("exportXtcLspModel").getOutput().contains("Configuration cache entry reused"));
        // with the reports gone, the export runs again from the entry that the configuration cache kept, and says the same
        Files.delete(directory.resolve("released/build/xtc/lsp/main.json"));
        Files.delete(directory.resolve(".gradle/xtc/lsp-model.json"));
        final var reused = run("exportXtcLspModel");
        assertTrue(reused.getOutput().contains("Configuration cache entry reused"));
        assertEquals(TaskOutcome.SUCCESS, reused.task(":released:exportXtcLspInputs").getOutcome());
        assertEquals(expected, entry(":released", "main").get("xdk"));
        // without the configuration cache, the export says the same
        Files.delete(directory.resolve(".gradle/xtc/lsp-model.json"));
        GradleRunner.create().withProjectDir(directory.toFile()).withPluginClasspath()
            .withArguments("exportXtcLspModel", "--no-configuration-cache", "--no-watch-fs", "--stacktrace").build();
        assertEquals(expected, entry(":released", "main").get("xdk"));
        assertEquals(":built", ((Map<?, ?>) entry(":local", "main").get("xdk")).get("project"));

        // extractXdk replaces what an earlier XDK left in its directory, rather than adding to it: after 9.9.9, 9.9.8,
        // which lacks its turtle and javatools, leaves only its own module
        assertEquals(TaskOutcome.SUCCESS, run(":released:extractXdk").task(":released:extractXdk").getOutcome());
        assertEquals(List.of("ecstasy.xtc", "javatools.jar", "javatools_turtle.xtc"), extracted("released"));
        write("released/build.gradle.kts", repositoryXdk("9.9.8"));
        assertEquals(TaskOutcome.SUCCESS, run(":released:extractXdk").task(":released:extractXdk").getOutcome());
        assertEquals(List.of("ecstasy.xtc"), extracted("released"));
    }

    @Test
    void anXdkThatAnImportCouldNotDownloadIsDownloadedByTheNextOneThoughTheConfigurationCacheKeptTheFirst() throws IOException {
        directory = directory.toRealPath();
        xdk("9.9.9", Map.of("xdk/lib/ecstasy.xtc", bytes("the library")));
        // a repository whose server fails to send the archive, as a download can fail, until it is well again
        final var failing = new AtomicBoolean(true);
        final var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            final var file = directory.resolve("repository").resolve(exchange.getRequestURI().getPath().substring(1)).normalize();
            final var archive = file.getFileName().toString().endsWith(".zip");
            if ((failing.get() && archive) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(archive && failing.get() ? 500 : 404, -1);
            } else if (exchange.getRequestMethod().equals("HEAD")) {
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(Files.size(file)));
                exchange.sendResponseHeaders(200, -1);
            } else {
                final var content = Files.readAllBytes(file);
                exchange.sendResponseHeaders(200, content.length);
                exchange.getResponseBody().write(content);
            }
            exchange.close();
        });
        server.start();
        try {
            write("settings.gradle.kts", "rootProject.name = \"model-test\"\n");
            write("build.gradle.kts", """
                plugins { id("org.xtclang.xtc-plugin") }
                version = "1.0"
                repositories { maven { url = uri("http://127.0.0.1:%d/"); isAllowInsecureProtocol = true } }
                dependencies { add("xdkDistribution", "org.xtclang:xdk:9.9.9@zip") }
                """.formatted(server.getAddress().getPort()));
            write("src/main/x/App.x", "module App {}");
            final var first = run("exportXtcLspModel");
            assertTrue(first.getOutput().contains("Configuration cache entry stored"));
            assertEquals("the import could not fetch its distribution", ((Map<?, ?>) entry(":", "main").get("xdk")).get("problem"));
            failing.set(false);
            // the problem's export left a token that the next import reads as it configures the build, so that it does so
            // again rather than reuse the entry that kept the failed download
            final var again = run("exportXtcLspModel");
            assertTrue(again.getOutput().contains("xdk-retry"), again.getOutput());
            assertEquals(List.of(sha256("the library")), ((Map<?, ?>) entry(":", "main").get("xdk")).get("modules"));
            // and once it is well, an import reuses the entry
            assertTrue(run("exportXtcLspModel").getOutput().contains("Configuration cache entry reused"));
        } finally {
            server.stop(0);
        }
    }

    /** A build script of a project whose XDK is {@code version} of org.xtclang:xdk from the test's repository. */
    private static String repositoryXdk(final String version) {
        return """
            plugins { id("org.xtclang.xtc-plugin") }
            version = "1.0"
            repositories { maven { url = uri("../repository") } }
            dependencies { add("xdkDistribution", "org.xtclang:xdk:%s@zip") }
            """.formatted(version);
    }

    /** Publishes {@code version} of org.xtclang:xdk with the {@code entries} of its archive to the test's repository; the archive. */
    private Path xdk(final String version, final Map<String, byte[]> entries) throws IOException {
        final var archive = pom(version).resolve("xdk-" + version + ".zip");
        try (var zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (final var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return archive;
    }

    /** Publishes the metadata of {@code version} of org.xtclang:xdk to the test's repository, without its archive; its directory. */
    private Path pom(final String version) throws IOException {
        final var release = directory.resolve("repository/org/xtclang/xdk/" + version);
        Files.createDirectories(release);
        Files.writeString(release.resolve("xdk-" + version + ".pom"), """
            <project><modelVersion>4.0.0</modelVersion><groupId>org.xtclang</groupId><artifactId>xdk</artifactId>
            <version>%s</version><packaging>zip</packaging></project>
            """.formatted(version));
        return release;
    }

    /** A jar with the {@code entries}, as name and text, in pairs. */
    private static byte[] jar(final String... entries) throws IOException {
        final var content = new ByteArrayOutputStream();
        try (var jar = new ZipOutputStream(content)) {
            for (int each = 0; each < entries.length; each += 2) {
                jar.putNextEntry(new ZipEntry(entries[each]));
                jar.write(bytes(entries[each + 1]));
                jar.closeEntry();
            }
        }
        return content.toByteArray();
    }

    private static byte[] bytes(final String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** The files that {@code project}'s extractXdk extracted, by name. */
    private List<String> extracted(final String project) throws IOException {
        try (var files = Files.list(directory.resolve(project + "/build/xtc/xdk/lib"))) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    /** The SHA-256 of {@code text}'s UTF-8 bytes, in hex, as the export gives a file's. */
    private static String sha256(final String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(text)));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException(missing);
        }
    }

    /** The file URI of the directory at {@code path}, with its trailing slash, whether or not it is there. */
    private String folder(final String path) {
        final var uri = uri(path);
        return uri.endsWith("/") ? uri : uri + '/';
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
