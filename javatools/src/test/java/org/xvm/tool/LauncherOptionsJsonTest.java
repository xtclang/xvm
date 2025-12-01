package org.xvm.tool;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test JSON serialization and deserialization for LauncherOptions.
 */
class LauncherOptionsJsonTest {

    @Test
    void testCompilerOptionsJsonRoundTrip() {
        // Create options via Builder
        final var original = LauncherOptions.CompilerOptions.builder()
                .enableVerbose()
                .enableShowVersion()
                .forceRebuild()
                .enableStrictMode()
                .addModulePath(new File("/lib/path1"))
                .addModulePath(new File("/lib/path2"))
                .addResourceLocation(new File("/res"))
                .setOutputLocation(new File("/out"))
                .setModuleVersion("1.0.0")
                .addInputFile(new File("Foo.x"))
                .addInputFile(new File("Bar.x"))
                .build();

        System.out.println("Original module paths: " + original.getModulePath());

        // Serialize to JSON
        final String json = original.toJson();
        assertNotNull(json);
        System.out.println("JSON:\n" + json);
        assertTrue(json.contains("\"verbose\""));
        assertTrue(json.contains("\"rebuild\""));
        assertTrue(json.contains("\"strict\""));

        // Deserialize from JSON
        final var restored = LauncherOptions.CompilerOptions.fromJson(json);
        System.out.println("Restored module paths: " + restored.getModulePath());

        // Verify all options match
        assertEquals(original.isVerbose(), restored.isVerbose());
        assertEquals(original.isForcedRebuild(), restored.isForcedRebuild());
        assertEquals(original.isStrict(), restored.isStrict());
        assertEquals(original.getModulePath(), restored.getModulePath());
        assertEquals(original.getInputLocations(), restored.getInputLocations());
        assertEquals(original.getResourceLocations(), restored.getResourceLocations());
        assertEquals(original.getOutputLocation(), restored.getOutputLocation());
        assertEquals(original.getVersion(), restored.getVersion());
    }

    @Test
    void testRunnerOptionsJsonRoundTrip() {
        // Create options with injections
        final var original = LauncherOptions.RunnerOptions.builder()
                .enableVerbose()
                .enableJit()
                .setMethodName("main")
                .addModulePath(new File("/lib"))
                .addInjection("dbUrl", "jdbc:mysql://localhost/db")
                .addInjection("apiKey", "secret123")
                .setTarget(new File("MyApp.xtc"), "arg1", "arg2")
                .build();

        // Serialize to JSON
        final String json = original.toJson();
        assertNotNull(json);

        // Deserialize from JSON
        final var restored = LauncherOptions.RunnerOptions.fromJson(json);

        // Verify all options match
        assertEquals(original.isVerbose(), restored.isVerbose());
        assertEquals(original.isJit(), restored.isJit());
        assertEquals(original.getMethodName(), restored.getMethodName());
        assertEquals(original.getModulePath(), restored.getModulePath());
        assertEquals(original.getTarget(), restored.getTarget());
        assertEquals(original.getMethodArgs(), restored.getMethodArgs());
        assertEquals(original.getInjections(), restored.getInjections());
    }

    @Test
    void testDisassemblerOptionsJsonRoundTrip() {
        // Create disassembler options
        final var original = LauncherOptions.DisassemblerOptions.builder()
                .enableVerbose()
                .listEmbeddedFiles()
                .addModulePath(new File("/lib"))
                .setTarget("Module.xtc")
                .build();

        // Serialize to JSON
        final String json = original.toJson();
        System.out.println(json);
        assertNotNull(json);

        // Deserialize from JSON
        final var restored = LauncherOptions.DisassemblerOptions.fromJson(json);
        System.err.println(restored);

        // Verify all options match
        assertEquals(original.isVerbose(), restored.isVerbose());
        assertEquals(original.isListFiles(), restored.isListFiles());
        assertEquals(original.getModulePath(), restored.getModulePath());
        assertEquals(original.getTarget(), restored.getTarget());
    }

    @Test
    void testJsonPrettyPrinting() {
        final var options = LauncherOptions.CompilerOptions.builder()
                .enableVerbose()
                .addInputFile("Test.x")
                .build();
        final String json = options.toJson();
        System.out.println(json);
        // Verify pretty printing (should have newlines)
        assertTrue(json.contains("\n"));
        assertTrue(json.contains("  ")); // Should have indentation
    }

    @Test
    void testEmptyOptions() {
        // Parse minimal options
        final var original = LauncherOptions.CompilerOptions.parse(new String[]{"Test.x"});
        final String json = original.toJson();
        System.out.println(json);
        final var restored = LauncherOptions.CompilerOptions.fromJson(json);
        assertEquals(original.isVerbose(), restored.isVerbose());
        assertEquals(original.getInputLocations(), restored.getInputLocations());
    }
}
