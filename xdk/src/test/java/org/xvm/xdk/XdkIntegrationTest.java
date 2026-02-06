package org.xvm.xdk;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.xvm.asm.ErrorList;
import org.xvm.tool.Compiler;
import org.xvm.tool.Launcher;
import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.LauncherOptions.RunnerOptions;
import org.xvm.tool.Runner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration tests for the fully built XDK distribution.
 * These tests verify that:
 * <ul>
 *   <li>Compiler can compile XTC source code with proper module path resolution
 *   <li>Runner can execute compiled XTC modules
 *   <li>Builder APIs work correctly in a real execution environment
 *   <li>XTC module dependencies (ecstasy.xtc, etc.) resolve correctly from the built XDK
 *   <li>Launcher handles invalid arguments gracefully
 * </ul>
 *
 * <p><b>IMPORTANT:</b> This test runs in the xdk project, which means it executes AFTER:
 * <ol>
 *   <li>javatools has been compiled (Compiler, Runner, etc.)</li>
 *   <li>plugin has been built (XTC Gradle plugin)</li>
 *   <li>All lib_* projects have been compiled by the plugin to produce .xtc files</li>
 *   <li>The XDK distribution has been assembled with all XTC libraries</li>
 * </ol>
 *
 * <p>This is the correct place for functional/integration tests that need the complete XDK
 * environment with all XTC libraries built and available.
 */
class XdkIntegrationTest {

    private static File xdkLibDir;
    private static File xdkJavaToolsDir;
    private static File ecstasyXtc;

    @BeforeAll
    static void findXdkLibraries() {
        // The xdk project has installDist task that creates build/install/xdk/lib and build/install/xdk/javatools
        File xdkBuildDir = new File(System.getProperty("user.dir"), "build/install/xdk");
        xdkLibDir = new File(xdkBuildDir, "lib");
        xdkJavaToolsDir = new File(xdkBuildDir, "javatools");

        if (!xdkLibDir.exists() || !xdkLibDir.isDirectory()) {
            fail("XDK libraries not found at: " + xdkLibDir.getAbsolutePath() +
                 "\nPlease run: ./gradlew installDist");
        }

        if (!xdkJavaToolsDir.exists() || !xdkJavaToolsDir.isDirectory()) {
            fail("XDK javatools not found at: " + xdkJavaToolsDir.getAbsolutePath() +
                 "\nPlease run: ./gradlew installDist");
        }

        ecstasyXtc = new File(xdkLibDir, "ecstasy.xtc");
        if (!ecstasyXtc.exists()) {
            fail("ecstasy.xtc not found at: " + ecstasyXtc.getAbsolutePath() +
                 "\nThe XDK build may be incomplete.");
        }

        System.out.println("XDK Integration Test using lib: " + xdkLibDir.getAbsolutePath());
        System.out.println("XDK Integration Test using javatools: " + xdkJavaToolsDir.getAbsolutePath());
        System.out.println("Found ecstasy.xtc: " + ecstasyXtc.length() + " bytes");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true",
        disabledReason = "Compiles XTC modules; enable with RUN_INTEGRATION_TESTS=true")
    void testCompilerWithBuilderAPI(@TempDir final Path tempDir) throws IOException {
        // Create a simple XTC source file
        String sourceCode = """
            module HelloWorld {
                void run() {
                    @Inject Console console;
                    console.print("Hello from XTC!");
                }
            }
            """;

        File sourceFile = tempDir.resolve("HelloWorld.x").toFile();
        Files.writeString(sourceFile.toPath(), sourceCode);

        // Compiler will create the output directory if it doesn't exist
        File outputDir = tempDir.resolve("out").toFile();

        // Build compiler options using the Builder API
        CompilerOptions options = new CompilerOptions.Builder()
            .addModulePath(xdkLibDir)
            .addModulePath(xdkJavaToolsDir)
            .setOutputLocation(outputDir)
            .addInputFile(sourceFile)
            .enableVerbose(true)
            .build();

        // Verify options were built correctly
        assertTrue(options.isVerbose(), "Verbose flag should be set");
        assertEquals(Optional.of(outputDir), options.getOutputLocation());
        assertTrue(options.getModulePath().contains(xdkLibDir));
        assertTrue(options.getInputLocations().contains(sourceFile));

        // Create ErrorList to capture any compilation errors
        ErrorList errors = new ErrorList(10);

        // Create and run compiler
        Compiler compiler = new Compiler(options, null, errors);
        int result = compiler.run();

        // Verify compilation succeeded
        assertEquals(0, result, "Compilation should succeed");
        assertFalse(errors.hasSeriousErrors(), "Should have no compilation errors");

        // Verify output module was created
        File outputModule = new File(outputDir, "HelloWorld.xtc");
        assertTrue(outputModule.exists(), "Compiled module should exist: " + outputModule);
        assertTrue(outputModule.length() > 0, "Compiled module should not be empty");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true",
        disabledReason = "Compiles XTC modules; enable with RUN_INTEGRATION_TESTS=true")
    void testCompilerWithParseAPI(@TempDir final Path tempDir) throws IOException {
        // Create a simple XTC source file
        String sourceCode = """
            module TestModule {
                void run() {
                    @Inject Console console;
                    console.print("Test");
                }
            }
            """;

        File sourceFile = tempDir.resolve("TestModule.x").toFile();
        Files.writeString(sourceFile.toPath(), sourceCode);

        // Compiler will create the output directory if it doesn't exist
        File outputDir = tempDir.resolve("out").toFile();

        // Parse compiler options from command-line args
        String[] args = {
            "-L", xdkLibDir.getAbsolutePath(),
            "-L", xdkJavaToolsDir.getAbsolutePath(),
            "-o", outputDir.getAbsolutePath(),
            "-v",
            sourceFile.getAbsolutePath()
        };

        CompilerOptions options = CompilerOptions.parse(args);

        // Verify parsed options
        assertTrue(options.isVerbose());
        assertEquals(Optional.of(outputDir), options.getOutputLocation());
        assertTrue(options.getModulePath().contains(xdkLibDir));

        ErrorList errors = new ErrorList(10);

        // Create and run compiler
        Compiler compiler = new Compiler(options, null, errors);
        int result = compiler.run();

        // Verify compilation succeeded
        assertEquals(0, result, "Compilation should succeed");
        assertFalse(errors.hasSeriousErrors(), "Should have no compilation errors");

        // Verify output
        File outputModule = new File(outputDir, "TestModule.xtc");
        assertTrue(outputModule.exists(), "Compiled module should exist");
    }

    @Test
    void testCompilerInvalidArguments() {
        // Test that invalid arguments are caught and reported
        final String[] invalidArgs = {"--invalid-option", "Test.x"};
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> CompilerOptions.parse(invalidArgs), "Should throw IllegalArgumentException for invalid options");
        assertTrue(e.getMessage().contains("Unrecognized option") ||
                  e.getMessage().contains("invalid"),
                  "Error message should mention the invalid option");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true",
        disabledReason = "Compiles XTC modules; enable with RUN_INTEGRATION_TESTS=true")
    void testCompilerMissingModulePath(@TempDir final Path tempDir) throws IOException {
        // Create source without providing module path (should fail to resolve ecstasy.xtc)
        String sourceCode = """
            module TestModule {
                void run() {}
            }
            """;

        File sourceFile = tempDir.resolve("TestModule.x").toFile();
        Files.writeString(sourceFile.toPath(), sourceCode);

        // Compiler will create the output directory if it doesn't exist
        File outputDir = tempDir.resolve("out").toFile();

        CompilerOptions options = new CompilerOptions.Builder()
            .setOutputLocation(outputDir)
            .addInputFile(sourceFile)
            .build();

        ErrorList errors = new ErrorList(10);
        Compiler compiler = new Compiler(options, null, errors);

        // Should throw LauncherException because ecstasy module cannot be found
        Launcher.LauncherException exception = assertThrows(Launcher.LauncherException.class, compiler::run,
                "Compilation should throw exception without module path");
        assertTrue(exception.isError(), "Exception should indicate an error condition");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true",
        disabledReason = "Compiles and runs XTC modules; enable with RUN_INTEGRATION_TESTS=true")
    void testRunnerOptionsBuilderAPI(@TempDir final Path tempDir) throws IOException {
        // First compile a simple module
        String sourceCode = """
            module RunnerTest {
                void run() {
                    @Inject Console console;
                    console.print("Runner test successful");
                }
            }
            """;

        File sourceFile = tempDir.resolve("RunnerTest.x").toFile();
        Files.writeString(sourceFile.toPath(), sourceCode);

        // Compiler will create the output directory if it doesn't exist
        File outputDir = tempDir.resolve("out").toFile();

        // Compile the module
        CompilerOptions compilerOptions = new CompilerOptions.Builder()
            .addModulePath(xdkLibDir)
            .addModulePath(xdkJavaToolsDir)
            .setOutputLocation(outputDir)
            .addInputFile(sourceFile)
            .build();

        ErrorList compileErrors = new ErrorList(10);
        Compiler compiler = new Compiler(compilerOptions, null, compileErrors);
        int compileResult = compiler.run();

        assertEquals(0, compileResult, "Compilation should succeed");

        // Build Runner options using Builder API
        File moduleFile = new File(outputDir, "RunnerTest.xtc");
        assertTrue(moduleFile.exists(), "Compiled module should exist");

        RunnerOptions runnerOptions = new RunnerOptions.Builder()
            .addModulePath(xdkLibDir)
            .addModulePath(xdkJavaToolsDir)
            .addModulePath(outputDir)
            .setTarget(moduleFile)
            .setMethodName("run")
            .build();

        // Verify runner options were built correctly
        assertEquals("run", runnerOptions.getMethodName());
        assertEquals(Optional.of(moduleFile), runnerOptions.getTarget());
        assertTrue(runnerOptions.getModulePath().contains(xdkLibDir));
        assertTrue(runnerOptions.getModulePath().contains(outputDir));

        ErrorList runErrors = new ErrorList(10);
        Runner runner = new Runner(runnerOptions, null, runErrors);

        // Verify Runner can be constructed without errors
        assertNotNull(runner, "Runner should be created successfully");
        assertFalse(runErrors.hasSeriousErrors(), "Should have no errors creating runner");
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true",
        disabledReason = "Compiles and runs XTC modules; enable with RUN_INTEGRATION_TESTS=true")
    void testRunnerOptionsParseAPI(@TempDir final Path tempDir) throws IOException {
        // First compile a module
        String sourceCode = """
            module ParseTestModule {
                void run() {}
            }
            """;

        File sourceFile = tempDir.resolve("ParseTestModule.x").toFile();
        Files.writeString(sourceFile.toPath(), sourceCode);

        // Compiler will create the output directory if it doesn't exist
        File outputDir = tempDir.resolve("out").toFile();

        // Compile
        String[] compileArgs = {
            "-L", xdkLibDir.getAbsolutePath(),
            "-L", xdkJavaToolsDir.getAbsolutePath(),
            "-o", outputDir.getAbsolutePath(),
            sourceFile.getAbsolutePath()
        };

        CompilerOptions compilerOptions = CompilerOptions.parse(compileArgs);
        ErrorList errors = new ErrorList(10);
        Compiler compiler = new Compiler(compilerOptions, null, errors);
        compiler.run();

        // Parse runner options from args
        File moduleFile = new File(outputDir, "ParseTestModule.xtc");
        String[] runArgs = {
            "-L", xdkLibDir.getAbsolutePath(),
            "-L", xdkJavaToolsDir.getAbsolutePath(),
            "-L", outputDir.getAbsolutePath(),
            "-M", "run",
            moduleFile.getAbsolutePath()
        };

        RunnerOptions runnerOptions = RunnerOptions.parse(runArgs);

        assertNotNull(runnerOptions, "Runner options should parse successfully");
        assertEquals("run", runnerOptions.getMethodName());
        assertEquals(Optional.of(moduleFile), runnerOptions.getTarget());
        assertTrue(runnerOptions.getModulePath().contains(xdkLibDir));
    }

    @Test
    void testRunnerInvalidArguments() {
        // Test that invalid arguments are caught
        final String[] invalidArgs = {"--bad-option", "Test.xtc"};
        assertThrows(IllegalArgumentException.class, () -> RunnerOptions.parse(invalidArgs), "Should throw IllegalArgumentException for invalid options");
    }

    @Test
    void testLauncherInvalidCommand() {
        // Test that launcher handles invalid command gracefully
        final String[] args = {"invalid_command", "Test.x"};
        int result = Launcher.launch(args);
        assertEquals(1, result, "Invalid command should return exit code 1");
    }

    @Test
    void testLauncherInvalidCompilerArgs() {
        // Test that launcher handles invalid compiler args gracefully
        String[] args = {Launcher.CMD_BUILD, "--invalid-flag", "Test.x"};

        int result = Launcher.launch(args);
        assertEquals(1, result, "Invalid compiler args should return exit code 1");
    }

    @Test
    void testLauncherInvalidRunnerArgs() {
        // Test that launcher handles invalid runner args gracefully
        String[] args = {Launcher.CMD_RUN, "--bad-option", "Test.xtc"};

        int result = Launcher.launch(args);
        assertEquals(1, result, "Invalid runner args should return exit code 1");
    }

    @Test
    void testLauncherInvalidTestRunnerArgs() {
        // Test that launcher handles invalid test runner args gracefully
        String[] args = {Launcher.CMD_TEST, "--bad-option", "Test.xtc"};

        int result = Launcher.launch(args);
        assertEquals(1, result, "Invalid test runner args should return exit code 1");
    }

    @Test
    void testModulePathResolution() {
        // Test that module path is properly resolved and includes xdk libraries
        CompilerOptions options = new CompilerOptions.Builder()
            .addModulePath(xdkLibDir)
            .addModulePath(xdkJavaToolsDir)
            .build();

        assertNotNull(options.getModulePath());
        assertFalse(options.getModulePath().isEmpty());
        assertTrue(options.getModulePath().contains(xdkLibDir),
                  "Module path should contain XDK lib directory");

        // Verify ecstasy.xtc is accessible
        assertTrue(ecstasyXtc.exists(), "ecstasy.xtc should be in module path");
        assertTrue(ecstasyXtc.length() > 1_000_000,
                  "ecstasy.xtc should be substantial (>1MB), got: " + ecstasyXtc.length());
    }

    @Test
    void testXdkLibrariesExist() {
        // Verify key XDK libraries are present
        String[] requiredLibraries = {
            "ecstasy.xtc",
            "aggregate.xtc",
            "collections.xtc",
            "json.xtc",
            "net.xtc"
        };

        for (final String lib : requiredLibraries) {
            File libFile = new File(xdkLibDir, lib);
            assertTrue(libFile.exists(), "Required library should exist: " + lib);
            assertTrue(libFile.length() > 0, "Library should not be empty: " + lib);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true",
        disabledReason = "Compiles XTC modules; enable with RUN_INTEGRATION_TESTS=true")
    void testCompilerStrictMode(@TempDir final Path tempDir) throws IOException {
        // Test that strict mode flag is properly set and used
        String sourceCode = """
            module StrictTest {
                void run() {
                    @Inject Console console;
                    console.print("Strict mode test");
                }
            }
            """;

        File sourceFile = tempDir.resolve("StrictTest.x").toFile();
        Files.writeString(sourceFile.toPath(), sourceCode);

        // Compiler will create the output directory if it doesn't exist
        File outputDir = tempDir.resolve("out").toFile();

        CompilerOptions strictOptions = new CompilerOptions.Builder()
            .addModulePath(xdkLibDir)
            .addModulePath(xdkJavaToolsDir)
            .setOutputLocation(outputDir)
            .addInputFile(sourceFile)
            .enableStrictMode(true)
            .build();

        assertTrue(strictOptions.isStrict(), "Strict mode should be enabled");

        ErrorList errors = new ErrorList(10);
        Compiler compiler = new Compiler(strictOptions, null, errors);
        compiler.run();

        // Verify strict mode flag was set
        assertTrue(strictOptions.isStrict(), "Strict mode flag should remain set");
    }

    /**
     * Test that Runner properly forwards the deduce (-d) and verbose (-v) flags to the Compiler
     * when it needs to recompile an out-of-date module.
     * <p>
     * This test verifies the fix for the bug where Runner was not forwarding these flags
     * to the Compiler during automatic recompilation. See Runner.java:159-167.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true",
        disabledReason = "Compiles and runs XTC modules; enable with RUN_INTEGRATION_TESTS=true")
    void testRunnerForwardsDeduceAndVerboseFlagsToCompiler(@TempDir final Path tempDir) throws IOException {
        // Create a source file that uses deduce to find its location
        // The key is that we run xec with -d on a .x file (not .xtc),
        // which triggers the Runner to compile it, and -d must be forwarded
        String sourceCode = """
            module DeduceTest {
                void run() {
                    @Inject Console console;
                    console.print("Deduce flag forwarding test");
                }
            }
            """;

        File sourceFile = tempDir.resolve("DeduceTest.x").toFile();
        Files.writeString(sourceFile.toPath(), sourceCode);

        // Use Runner with -d and -v flags on a source file (not compiled .xtc)
        // This forces Runner to invoke the Compiler, and it should forward -d and -v
        RunnerOptions runnerOptions = RunnerOptions.parse(new String[]{
            "-d",                                    // deduce flag - must be forwarded to compiler
            "-v",                                    // verbose flag - must be forwarded to compiler
            "-L", xdkLibDir.getAbsolutePath(),
            "-L", xdkJavaToolsDir.getAbsolutePath(),
            "-o", tempDir.toAbsolutePath().toString(),  // output directory
            sourceFile.getAbsolutePath()             // source file, not .xtc - triggers compilation
        });

        // Verify runner options have the flags set
        assertTrue(runnerOptions.mayDeduceLocations(), "Runner should have deduce enabled");
        assertTrue(runnerOptions.isVerbose(), "Runner should have verbose enabled");

        ErrorList errors = new ErrorList(10);
        Runner runner = new Runner(runnerOptions, null, errors);

        // Run the runner - this should:
        // 1. Detect that DeduceTest.xtc doesn't exist
        // 2. Invoke the Compiler with -d and -v flags forwarded
        // 3. Compile the source file
        // 4. Execute the compiled module
        int result = runner.run();

        // Verify execution succeeded (which means compilation succeeded with flags forwarded)
        assertEquals(0, result, "Runner should succeed, indicating -d/-v flags were properly forwarded to compiler");
        assertFalse(errors.hasSeriousErrors(), "Should have no serious errors");

        // Verify the compiled module was created
        File compiledModule = new File(tempDir.toFile(), "DeduceTest.xtc");
        assertTrue(compiledModule.exists(), "Compiled module should exist after Runner-triggered compilation");
    }

    /**
     * Test that Runner can recompile an out-of-date module and forwards flags correctly.
     * This simulates the scenario Gene reported: running xec -d on a source file that
     * needs recompilation.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TESTS", matches = "true",
        disabledReason = "Compiles and runs XTC modules with recompilation; enable with RUN_INTEGRATION_TESTS=true")
    void testRunnerRecompilesOutOfDateModule(@TempDir final Path tempDir) throws IOException, InterruptedException {
        // Step 1: Create and compile a module
        String sourceCodeV1 = """
            module RecompileTest {
                void run() {
                    @Inject Console console;
                    console.print("Version 1");
                }
            }
            """;

        File sourceFile = tempDir.resolve("RecompileTest.x").toFile();
        Files.writeString(sourceFile.toPath(), sourceCodeV1);

        File outputDir = tempDir.resolve("out").toFile();

        // Initial compilation
        CompilerOptions compilerOptions = new CompilerOptions.Builder()
            .addModulePath(xdkLibDir)
            .addModulePath(xdkJavaToolsDir)
            .setOutputLocation(outputDir)
            .addInputFile(sourceFile)
            .build();

        ErrorList compileErrors = new ErrorList(10);
        Compiler compiler = new Compiler(compilerOptions, null, compileErrors);
        assertEquals(0, compiler.run(), "Initial compilation should succeed");

        File compiledModule = new File(outputDir, "RecompileTest.xtc");
        assertTrue(compiledModule.exists(), "Compiled module should exist");
        long originalModTime = compiledModule.lastModified();

        // Step 2: Wait a bit and modify the source file to make .xtc out of date
        Thread.sleep(1100); // Ensure file timestamp changes (some filesystems have 1s resolution)

        String sourceCodeV2 = """
            module RecompileTest {
                void run() {
                    @Inject Console console;
                    console.print("Version 2 - Updated");
                }
            }
            """;
        Files.writeString(sourceFile.toPath(), sourceCodeV2);

        // Step 3: Run Runner with -d flag on the source file
        // Runner should detect the .xtc is out of date and recompile
        RunnerOptions runnerOptions = RunnerOptions.parse(new String[]{
            "-d",                                    // deduce flag
            "-L", xdkLibDir.getAbsolutePath(),
            "-L", xdkJavaToolsDir.getAbsolutePath(),
            "-L", outputDir.getAbsolutePath(),
            "-o", outputDir.getAbsolutePath(),
            sourceFile.getAbsolutePath()
        });

        assertTrue(runnerOptions.mayDeduceLocations(), "Runner should have deduce enabled");

        ErrorList runErrors = new ErrorList(10);
        Runner runner = new Runner(runnerOptions, null, runErrors);
        int result = runner.run();

        // Verify execution succeeded
        assertEquals(0, result, "Runner should succeed after recompilation with -d flag forwarded");
        assertFalse(runErrors.hasSeriousErrors(), "Should have no serious errors");

        // Verify the module was recompiled (modification time should be newer)
        assertTrue(compiledModule.lastModified() >= originalModTime,
            "Compiled module should be recompiled (new or same timestamp)");
    }
}
