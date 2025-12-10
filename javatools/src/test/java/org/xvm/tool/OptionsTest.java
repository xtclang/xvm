package org.xvm.tool;

import org.junit.jupiter.api.Test;
import org.xvm.asm.Version;
import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.LauncherOptions.RunnerOptions;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Options2 system.
 * Tests parsing, building, and serialization for CompilerOptions and RunnerOptions.
 */
class OptionsTest {

    @Test
    void testCompilerOptionsParseFromArgs() {
        final var args = new String[]{"--rebuild", "--strict", "-L", "/lib1", "-o", "/tmp/out", "foo.x", "bar.x"};
        final var opts = CompilerOptions.parse(args);

        assertTrue(opts.isForcedRebuild());
        assertTrue(opts.isStrict());
        assertFalse(opts.isNoWarn());
        assertEquals(new File("/tmp/out"), opts.getOutputLocation());

        final var modulePath = opts.getModulePath();
        assertEquals(1, modulePath.size());
        assertEquals(new File("/lib1"), modulePath.getFirst());

        final var sources = opts.getInputLocations();
        assertEquals(2, sources.size());
        assertEquals(new File("foo.x"), sources.get(0));
        assertEquals(new File("bar.x"), sources.get(1));
    }

    @Test
    void testCompilerOptionsBuilder() {
        final var opts = new CompilerOptions.Builder()
                .forceRebuild(true)
                .enableStrictMode(true)
                .addModulePath(new File("/lib1"))
                .addModulePath(new File("/lib2"))
                .setOutputLocation(new File("/tmp/out"))
                .addInputFile(new File("foo.x"))
                .addInputFile(new File("bar.x"))
                .setModuleVersion("1.2.3")
                .build();

        assertTrue(opts.isForcedRebuild());
        assertTrue(opts.isStrict());
        assertEquals(new File("/tmp/out"), opts.getOutputLocation());
        assertEquals(new Version("1.2.3"), opts.getVersion());

        final var modulePath = opts.getModulePath();
        assertEquals(2, modulePath.size());
        assertEquals(new File("/lib1"), modulePath.get(0));
        assertEquals(new File("/lib2"), modulePath.get(1));

        final var sources = opts.getInputLocations();
        assertEquals(2, sources.size());
        assertEquals(new File("foo.x"), sources.get(0));
        assertEquals(new File("bar.x"), sources.get(1));
    }

    @Test
    void testCompilerOptionsSerializeToArgs() {
        final var opts = new CompilerOptions.Builder()
                .forceRebuild(true)
                .enableStrictMode(true)
                .addModulePath(new File("/lib1"))
                .setOutputLocation(new File("/tmp/out"))
                .addInputFile(new File("foo.x"))
                .build();

        String[] args = opts.toCommandLine();

        // Should produce args like: ["--rebuild", "--strict", "-L", "/lib1", "-o", "/tmp/out", "foo.x"]
        assertTrue(contains(args, "--rebuild"));
        assertTrue(contains(args, "--strict"));
        assertTrue(contains(args, "-L"));
        assertTrue(contains(args, "/lib1"));
        assertTrue(contains(args, "-o"));
        assertTrue(contains(args, "/tmp/out"));
        assertTrue(contains(args, "foo.x"));
    }

    @Test
    void testCompilerOptionsRoundTrip() {
        String[] originalArgs = {"--rebuild", "-L", "/lib1", "-o", "/tmp/out", "foo.x"};

        final var opts = CompilerOptions.parse(originalArgs);
        String[] serializedArgs = opts.toCommandLine();
        CompilerOptions reparsed = CompilerOptions.parse(serializedArgs);

        assertEquals(opts.isForcedRebuild(), reparsed.isForcedRebuild());
        assertEquals(opts.getOutputLocation(), reparsed.getOutputLocation());
        assertEquals(opts.getModulePath(), reparsed.getModulePath());
        assertEquals(opts.getInputLocations(), reparsed.getInputLocations());
    }

    @Test
    void testRunnerOptionsParseFromArgs() {
        final String[] args = {"-J", "-M", "main", "-I", "key1=value1", "-I", "key2=value2",
                         "-L", "/lib1", "MyModule.xtc", "arg1", "arg2"};

        final var opts = RunnerOptions.parse(args);

        assertTrue(opts.isJit());
        assertEquals("main", opts.getMethodName());
        assertFalse(opts.isCompileDisabled());
        assertEquals(new File("MyModule.xtc"), opts.getTarget());

        assertEquals(Map.of("key1", List.of("value1"), "key2", List.of("value2")), opts.getInjections());
        assertEquals(List.of("arg1", "arg2"), opts.getMethodArgs());
        assertEquals(List.of(new File("/lib1")), opts.getModulePath());
    }

    @Test
    void testRunnerOptionsBuilderWithDefaults() {
        final var opts = RunnerOptions.parse(new String[]{"MyModule.xtc"});

        assertFalse(opts.isJit());
        assertEquals("run", opts.getMethodName());
        assertEquals(new File("MyModule.xtc"), opts.getTarget());
        assertTrue(opts.getMethodArgs().isEmpty());
        assertTrue(opts.getInjections().isEmpty());
    }

    @Test
    void testCompilerOptionsImmutability() {
        final var opts = new CompilerOptions.Builder()
                .addModulePath(new File("/lib1"))
                .build();

        // Options should be immutable - getModulePath returns unmodifiable list
        assertThrows(UnsupportedOperationException.class, () -> opts.getModulePath().add(new File("bad")));
    }

    @Test
    void testModulePathMultipleForms() {
        // Test 1: Single -L with colon-separated paths (Unix style)
        final var sep = File.pathSeparator; // : on Unix, ; on Windows
        String[] args1 = {"-L", "/lib" + sep + "/build" + sep + "/modules", "Test.x"};
        CompilerOptions opts1 = CompilerOptions.parse(args1);
        final var paths1 = opts1.getModulePath();
        assertEquals(3, paths1.size());
        assertEquals(new File("/lib"), paths1.get(0));
        assertEquals(new File("/build"), paths1.get(1));
        assertEquals(new File("/modules"), paths1.get(2));

        // Test 2: Multiple -L flags
        String[] args2 = {"-L", "/lib", "-L", "/build", "-L", "/modules", "Test.x"};
        CompilerOptions opts2 = CompilerOptions.parse(args2);
        final var paths2 = opts2.getModulePath();
        assertEquals(3, paths2.size());
        assertEquals(new File("/lib"), paths2.get(0));
        assertEquals(new File("/build"), paths2.get(1));
        assertEquals(new File("/modules"), paths2.get(2));

        // Test 3: Combination of both forms
        String[] args3 = {"-L", "/lib" + sep + "/build", "-L", "/modules", "Test.x"};
        CompilerOptions opts3 = CompilerOptions.parse(args3);
        final var paths3 = opts3.getModulePath();
        assertEquals(3, paths3.size());
        assertEquals(new File("/lib"), paths3.get(0));
        assertEquals(new File("/build"), paths3.get(1));
        assertEquals(new File("/modules"), paths3.get(2));
    }

    @Test
    void testInvalidOptionDetection() {
        // Apache CLI should throw IllegalArgumentException for unknown options
        assertThrows(IllegalArgumentException.class, () -> CompilerOptions.parse(new String[]{"--invalid-option", "Test.x"}));
        assertThrows(IllegalArgumentException.class, () -> RunnerOptions.parse(new String[]{"--bad-flag", "Test.xtc"}));
    }

    @Test
    void testMissingRequiredArgument() {
        // Option that requires an argument but doesn't get one
        assertThrows(IllegalArgumentException.class, () -> {
            CompilerOptions.parse(new String[]{"-L"});  // -L requires an argument
        });

        assertThrows(IllegalArgumentException.class, () -> {
            RunnerOptions.parse(new String[]{"-M"});  // -M requires an argument
        });
    }

    @Test
    void testRunnerOptionsImmutability() {
        final String[] args = {"-I", "key=value", "MyModule.xtc"};
        final var opts = RunnerOptions.parse(args);

        // Options should be immutable - getInjections returns unmodifiable map
        assertThrows(UnsupportedOperationException.class, () -> opts.getInjections().put("bad", List.of("value")));
    }

    @Test
    void testLongFormFlags() {
        final String[] args = {"--rebuild", "--strict", "foo.x"};

        final var opts = CompilerOptions.parse(args);

        assertTrue(opts.isForcedRebuild());
        assertTrue(opts.isStrict());
        assertFalse(opts.isNoWarn());
    }

    @Test
    void testNoWarnFlag() {
        final String[] args = {"--nowarn", "foo.x"};

        final var opts = CompilerOptions.parse(args);

        assertFalse(opts.isStrict());
        assertTrue(opts.isNoWarn());
    }

    @Test
    void testStrictAndNoWarnMutualExclusion() {
        // Test that --strict and --nowarn are mutually exclusive when parsing args
        final String[] args = {"--strict", "--nowarn", "foo.x"};

        assertThrows(IllegalArgumentException.class, () -> CompilerOptions.parse(args));
    }

    @Test
    void testStrictAndNoWarnMutualExclusionBuilder() {
        // Test that --strict and --nowarn are mutually exclusive when using builder
        assertThrows(IllegalArgumentException.class, () -> new CompilerOptions.Builder()
                .enableStrictMode(true)
                .disableWarnings(true)
                .addInputFile(new File("foo.x"))
                .build());
    }

    @Test
    void testMissingOptionalValues() {
        final String[] args = {"foo.x"};

        final var opts = CompilerOptions.parse(args);

        assertFalse(opts.isForcedRebuild());
        assertFalse(opts.isStrict());
        assertNull(opts.getOutputLocation());
        assertNull(opts.getVersion());
        assertTrue(opts.getModulePath().isEmpty());
    }

    @Test
    void testEmptyOptions() {
        // Test that empty constructor creates valid empty options
        final var opts = new CompilerOptions.Builder().build();

        assertNotNull(opts);
        assertFalse(opts.isForcedRebuild());
        assertTrue(opts.getInputLocations().isEmpty());
        assertTrue(opts.getModulePath().isEmpty());
    }

    // ----- Comprehensive edge case tests -----

    @Test
    void testFlagWithoutValue() {
        final String[] args = {"-v", "foo.x"};
        final var opts = CompilerOptions.parse(args);
        assertTrue(opts.isVerbose());
    }

    @Test
    void testFlagWithFileArg() {
        // Apache Commons CLI treats flags as presence/absence
        // Next arg is treated as trailing if not an option
        final String[] args = {"-v", "foo.x"};
        final var opts = CompilerOptions.parse(args);
        assertTrue(opts.isVerbose());
        assertEquals(1, opts.getInputLocations().size());
        assertEquals(new File("foo.x"), opts.getInputLocations().getFirst());
    }

    @Test
    void testRepeatedMultiArgs() {
        final String[] args = {"-L", "/lib1", "-L", "/lib2", "-L", "/lib3", "foo.x"};
        final var opts = CompilerOptions.parse(args);

        final var modulePath = opts.getModulePath();
        assertEquals(3, modulePath.size());
        assertEquals(new File("/lib1"), modulePath.get(0));
        assertEquals(new File("/lib2"), modulePath.get(1));
        assertEquals(new File("/lib3"), modulePath.get(2));
    }

    @Test
    void testMultipleTrailingArgs() {
        final String[] args = {"-v", "file1.x", "file2.x", "file3.x"};
        final var opts = CompilerOptions.parse(args);

        final var inputs = opts.getInputLocations();
        assertEquals(3, inputs.size());
        assertEquals(new File("file1.x"), inputs.get(0));
        assertEquals(new File("file2.x"), inputs.get(1));
        assertEquals(new File("file3.x"), inputs.get(2));
    }

    @Test
    void testTrailingArgsFollowedByModuleArgs() {
        final String[] args = {"-M", "main", "MyModule.xtc", "arg1", "arg2", "arg3"};
        final var opts = RunnerOptions.parse(args);

        assertEquals(new File("MyModule.xtc"), opts.getTarget());
        assertEquals(List.of("arg1", "arg2", "arg3"), opts.getMethodArgs());
    }

    @Test
    void testMixedShortAndLongFormOptions() {
        final String[] args = {"-v", "--rebuild", "-L", "/lib", "--strict", "foo.x"};
        final var opts = CompilerOptions.parse(args);

        assertTrue(opts.isVerbose());
        assertTrue(opts.isForcedRebuild());
        assertTrue(opts.isStrict());
        assertEquals(1, opts.getModulePath().size());
    }

    @Test
    void testUnknownOption() {
        final String[] args = {"--unknown", "foo.x"};

        final var e = assertThrows(IllegalArgumentException.class, () -> CompilerOptions.parse(args));
        assertTrue(e.getMessage().contains("Unrecognized option") || e.getMessage().contains("unknown"));
    }

    @Test
    void testMissingRequiredValue() {
        final String[] args = {"-o"}; // Missing value for -o

        final var e = assertThrows(IllegalArgumentException.class, () -> CompilerOptions.parse(args));
        assertTrue(e.getMessage().contains("Missing argument") || e.getMessage().contains("requires"));
    }

    @Test
    void testMultipleErrors() {
        final String[] args = {"--unknown1", "--unknown2", "foo.x"};

        final var e = assertThrows(IllegalArgumentException.class, () -> CompilerOptions.parse(args));
        // Apache CLI stops at first error
        final var message = e.getMessage();
        assertTrue(message.contains("unknown1") || message.contains("Unrecognized"),
            "Expected error in message: " + message);
    }

    @Test
    void testEmptyArgs() {
        final String[] args = {};
        final var opts = CompilerOptions.parse(args);
        assertNotNull(opts);
        assertTrue(opts.getInputLocations().isEmpty());
    }

    @Test
    void testNullArgs() {
        final var opts = CompilerOptions.parse(null);
        assertNotNull(opts);
        assertTrue(opts.getInputLocations().isEmpty());
    }

    @Test
    void testRepeatedInjections() {
        final String[] args = {"-I", "key1=value1", "-I", "key2=value2", "-I", "key3=value3", "MyModule.xtc"};
        final var opts = RunnerOptions.parse(args);

        final var injections = opts.getInjections();
        assertEquals(3, injections.size());
        assertEquals(List.of("value1"), injections.get("key1"));
        assertEquals(List.of("value2"), injections.get("key2"));
        assertEquals(List.of("value3"), injections.get("key3"));
    }

    @Test
    void testMultiValueInjections() {
        // Test that multiple -I flags with the same key accumulate values
        final String[] args = {"-I", "key=value1", "-I", "key=value2", "-I", "key=value3", "MyModule.xtc"};
        final var opts = RunnerOptions.parse(args);

        final var injections = opts.getInjections();
        assertEquals(1, injections.size());
        assertEquals(List.of("value1", "value2", "value3"), injections.get("key"));
    }

    @Test
    void testInvalidMapValue() {
        // Apache Commons CLI doesn't validate format - we parse, and it just won't have '='
        // So the map will be empty or have incorrect parsing
        final String[] args = {"-I", "invalidpair", "MyModule.xtc"};

        final var opts = RunnerOptions.parse(args);
        // Invalid format means it won't be parsed into the map correctly
        final var injections = opts.getInjections();
        assertTrue(injections.isEmpty() || !injections.containsKey("invalidpair"));
    }

    @Test
    void testRepoPathParsing() {
        final var pathSeparator = File.pathSeparator;
        final String[] args = {"-L", "/lib1" + pathSeparator + "/lib2" + pathSeparator + "/lib3", "foo.x"};
        final var opts = CompilerOptions.parse(args);

        final var modulePath = opts.getModulePath();
        assertEquals(3, modulePath.size());
        assertEquals(new File("/lib1"), modulePath.get(0));
        assertEquals(new File("/lib2"), modulePath.get(1));
        assertEquals(new File("/lib3"), modulePath.get(2));
    }

    @Test
    void testQuotedStringValue() {
        final String[] args = {"--set-version", "1.2.3", "foo.x"};
        final var opts = CompilerOptions.parse(args);

        final var version = opts.getVersion();
        assertNotNull(version);
        assertEquals("1.2.3", version.toString());
    }

    @Test
    void testHelpTextGeneration() {
        // Test that help text can be generated for each option type
        CompilerOptions compilerOpts = new CompilerOptions.Builder().build();
        final var compilerHelp = compilerOpts.getHelpText();
        assertNotNull(compilerHelp);
        assertTrue(compilerHelp.contains("-L"));
        assertTrue(compilerHelp.contains("-o"));
        assertTrue(compilerHelp.contains("--strict"));

        RunnerOptions runnerOpts = new RunnerOptions.Builder().build();
        final var runnerHelp = runnerOpts.getHelpText();
        assertNotNull(runnerHelp);
        assertTrue(runnerHelp.contains("-L"));
        assertTrue(runnerHelp.contains("-M"));
        assertTrue(runnerHelp.contains("-J"));
    }

    @Test
    void testPartiallyInvalidOptions() {
        // When some options are valid and some are invalid, parsing should fail immediately
        // Apache CLI will detect the first unknown option and throw an exception

        // Valid options followed by invalid option
        assertThrows(IllegalArgumentException.class, () -> CompilerOptions.parse(new String[] {"-L", "/lib", "--invalid-flag", "foo.x"}));
        // Invalid option followed by valid options
        assertThrows(IllegalArgumentException.class, () -> CompilerOptions.parse(new String[] {"--bad-option", "-L", "/lib", "foo.x"}));

        // Mix of valid, invalid, and file arguments
        assertThrows(IllegalArgumentException.class, () -> RunnerOptions.parse(new String[]{"-J", "--unknown", "-M", "main", "Test.xtc"}));
    }

    @Test
    void testCustomUsageLines() {
        CompilerOptions compilerOpts = new CompilerOptions.Builder().build();
        final var compilerUsage = compilerOpts.buildUsageLine("xtc");
        assertTrue(compilerUsage.contains("xtc"));
        assertTrue(compilerUsage.contains("source_files"));

        RunnerOptions runnerOpts = new RunnerOptions.Builder().build();
        final var runnerUsage = runnerOpts.buildUsageLine("xec");
        assertTrue(runnerUsage.contains("xec"));
        assertTrue(runnerUsage.contains("module_or_file"));
        assertTrue(runnerUsage.contains("args"));
    }

    @Test
    void testConflictingOptionsStrictAndNowarn() {
        // Test that --strict and --nowarn cannot be used together
        final String[] args = {"--strict", "--nowarn", "foo.x"};

        // Should throw IllegalArgumentException due to mutually exclusive options
        assertThrows(IllegalArgumentException.class, () -> CompilerOptions.parse(args),
                "Expected parse to fail when both --strict and --nowarn are specified");
    }

    @Test
    void testStrictOptionAlone() {
        // Test that --strict works when used alone
        final String[] args = {"--strict", "foo.x"};
        final var opts = CompilerOptions.parse(args);

        assertTrue(opts.isStrict());
        assertFalse(opts.isNoWarn());
    }

    @Test
    void testNoWarnOptionAlone() {
        // Test that --nowarn works when used alone
        final String[] args = {"--nowarn", "foo.x"};
        final var opts = CompilerOptions.parse(args);

        assertFalse(opts.isStrict());
        assertTrue(opts.isNoWarn());
    }

    // Helper
    private static boolean contains(final String[] array, final String value) {
        for (String s : array) {
            if (s.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
