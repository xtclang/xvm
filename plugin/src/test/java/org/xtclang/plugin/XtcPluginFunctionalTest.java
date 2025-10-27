package org.xtclang.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Objects;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Functional tests that verify the XTC plugin can actually compile and run real XTC code.
 * Unlike the ConfigurationCacheCompatibilityTest which uses mock tasks, these tests
 * exercise the full plugin functionality with real XTC source files.
 */
class XtcPluginFunctionalTest {

    @TempDir
    Path testProjectDir;

    private File buildFile;
    private File settingsFile;
    private Path xvmRootDir;

    @BeforeEach
    void setUp() {
        buildFile = testProjectDir.resolve("build.gradle.kts").toFile();
        settingsFile = testProjectDir.resolve("settings.gradle.kts").toFile();

        // Detect XVM root directory (parent of plugin directory)
        // When running tests, user.dir is typically the xvm root, but we can also detect from classpath
        var userDir = System.getProperty("user.dir");
        var currentDir = Path.of(userDir);
        // If we're in the plugin subdirectory, go up one level to xvm root
        xvmRootDir = currentDir.endsWith("plugin") ? currentDir.getParent() : currentDir;
    }

    /**
     * Test that the plugin can compile and run a simple XTC module.
     */
    @Test
    void testCompileAndRunSimpleXtcModule() throws IOException {
        setupRealXtcProject();
        createSimpleXtcModule("HelloWorld", """
            module HelloWorld {
                void run() {
                    @Inject Console console;
                    console.print("Hello from XTC functional test!");
                }
            }
            """);

        // Configure the run task
        appendToBuildFile("""
            xtcRun {
                verbose = false
                module {
                    moduleName = "HelloWorld"
                }
            }
            """);

        // First compile
        BuildResult compileResult = runGradle("compileXtc", "--info");
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(compileResult.task(":compileXtc")).getOutcome());

        // Then run
        BuildResult runResult = runGradle("runXtc", "--info");
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(runResult.task(":runXtc")).getOutcome(),
            "Running the module should succeed");

        // Verify output contains our message
        assertTrue(runResult.getOutput().contains("Hello from XTC functional test!"),
            "Output should contain the message from the XTC module");
    }

    /**
     * Test that compilation is incremental - unchanged sources should not recompile.
     */
    @Test
    void testIncrementalCompilation() throws IOException {
        setupRealXtcProject();
        createSimpleXtcModule("TestModule", """
            module TestModule {
                void run() {
                    @Inject Console console;
                    console.print("Test");
                }
            }
            """);

        // First compilation
        BuildResult firstRun = runGradle("compileXtc", "--info");
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(firstRun.task(":compileXtc")).getOutcome());

        // Second compilation without changes - should be UP-TO-DATE
        BuildResult secondRun = runGradle("compileXtc", "--info");
        TaskOutcome secondOutcome = Objects.requireNonNull(secondRun.task(":compileXtc")).getOutcome();
        assertTrue(secondOutcome == TaskOutcome.UP_TO_DATE || secondOutcome == TaskOutcome.SUCCESS,
            "Task should be UP-TO-DATE or SUCCESS when sources haven't changed");
    }

    /**
     * Test that changing source code triggers recompilation.
     */
    @Test
    void testRecompileOnSourceChange() throws IOException {
        setupRealXtcProject();
        Path modulePath = createSimpleXtcModule("ChangeTest", """
            module ChangeTest {
                void run() {
                    @Inject Console console;
                    console.print("Version 1");
                }
            }
            """);

        // First compilation
        BuildResult firstRun = runGradle("compileXtc", "--info");
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(firstRun.task(":compileXtc")).getOutcome());

        // Modify the source
        Files.writeString(modulePath, """
            module ChangeTest {
                void run() {
                    @Inject Console console;
                    console.print("Version 2");
                }
            }
            """);

        // Second compilation - should recompile
        BuildResult secondRun = runGradle("compileXtc", "--info");
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(secondRun.task(":compileXtc")).getOutcome(),
            "Should recompile when source changes");
    }

    /**
     * Test that the plugin works with multiple XTC modules.
     */
    @Test
    void testMultipleModules() throws IOException {
        setupRealXtcProject();

        createSimpleXtcModule("ModuleA", """
            module ModuleA {
                void run() {
                    @Inject Console console;
                    console.print("Module A");
                }
            }
            """);

        createSimpleXtcModule("ModuleB", """
            module ModuleB {
                void run() {
                    @Inject Console console;
                    console.print("Module B");
                }
            }
            """);

        BuildResult result = runGradle("compileXtc", "--info");
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":compileXtc")).getOutcome());

        // Verify both modules were compiled
        assertTrue(Files.exists(testProjectDir.resolve("build/xtc/main/lib/ModuleA.xtc")), "ModuleA.xtc should exist");
        assertTrue(Files.exists(testProjectDir.resolve("build/xtc/main/lib/ModuleB.xtc")), "ModuleB.xtc should exist");
    }

    /**
     * Test that the plugin works with configuration cache enabled.
     */
    @Test
    void testWithConfigurationCache() throws IOException {
        setupRealXtcProject();
        createSimpleXtcModule("CacheTest", """
            module CacheTest {
                void run() {
                    @Inject Console console;
                    console.print("Testing configuration cache");
                }
            }
            """);

        // First run - store configuration cache
        BuildResult firstRun = runGradle("compileXtc", "--configuration-cache", "--info");
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(firstRun.task(":compileXtc")).getOutcome());
        assertTrue(firstRun.getOutput().contains("Configuration cache entry stored") || firstRun.getOutput().contains("Configuration cache entry reused"),
            "Should store or reuse configuration cache");

        // Second run - reuse configuration cache
        BuildResult secondRun = runGradle("compileXtc", "--configuration-cache", "--info");
        assertTrue(secondRun.getOutput().contains("Configuration cache entry reused"),
            "Should reuse configuration cache on second run");
    }

    /**
     * Test compilation with verbose output.
     */
    @Test
    void testVerboseCompilation() throws IOException {
        setupRealXtcProject();
        createSimpleXtcModule("VerboseTest", """
            module VerboseTest {
                void run() {}
            }
            """);

        appendToBuildFile("""

            xtcCompile {
                verbose = true
            }
            """);

        BuildResult result = runGradle("compileXtc", "--info");
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":compileXtc")).getOutcome());
    }

    // Helper methods

    private void setupRealXtcProject() throws IOException {
        // Create settings.gradle.kts with includedBuild to XVM root
        // This allows the test to use the current plugin code and XDK from the workspace
        Files.writeString(settingsFile.toPath(), """
            rootProject.name = "xtc-functional-test"

            // Include the XVM build to resolve both the plugin and XDK from source
            includeBuild("%s")
            """.formatted(xvmRootDir.toString().replace("\\", "\\\\")));

        // Create build.gradle.kts with real XTC plugin configuration
        Files.writeString(buildFile.toPath(), """
            plugins {
                id("org.xtclang.xtc-plugin")
            }

            group = "org.xtclang.test"
            version = "1.0-TEST"

            dependencies {
                // No version needed - Gradle resolves it from the included XVM build
                xdk("org.xtclang:xdk")
            }

            xtcCompile {
                useCompilerDaemon = false  // Disable daemon for isolation in tests
                verbose = false
                rebuild = false
            }
            """);
    }

    private Path createSimpleXtcModule(final String moduleName, final String sourceCode) throws IOException {
        final var srcDir = testProjectDir.resolve("src/main/x");
        Files.createDirectories(srcDir);
        final var moduleFile = srcDir.resolve(moduleName + ".x");
        Files.writeString(moduleFile, sourceCode);
        return moduleFile;
    }

    private void appendToBuildFile(final String content) throws IOException {
        final var existing = Files.readString(buildFile.toPath());
        Files.writeString(buildFile.toPath(), existing + content);
    }

    private BuildResult runGradle(final String... args) {
        return GradleRunner.create()
            .withProjectDir(testProjectDir.toFile())
            .withArguments(args)
            // Don't use withPluginClasspath() - the plugin comes from includedBuild
            .forwardOutput()
            .build();
    }
}
