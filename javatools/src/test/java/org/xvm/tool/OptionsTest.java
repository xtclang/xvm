package org.xvm.tool;

import org.junit.jupiter.api.Test;
import org.xvm.asm.Version;
import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.LauncherOptions.RunnerOptions;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Options2 system.
 * Tests parsing, building, and serialization for CompilerOptions and RunnerOptions.
 */
class OptionsTest {

    @Test
    void testCompilerOptionsParseFromArgs() {
        String[] args = {"--rebuild", "--strict", "-L", "/lib1", "-o", "/tmp/out", "foo.x", "bar.x"};

        CompilerOptions opts = CompilerOptions.parse(args);

        assertTrue(opts.isForcedRebuild());
        assertTrue(opts.isStrict());
        assertFalse(opts.isNoWarn());
        assertEquals(new File("/tmp/out"), opts.getOutputLocation());

        List<File> modulePath = opts.getModulePath();
        assertEquals(1, modulePath.size());
        assertEquals(new File("/lib1"), modulePath.get(0));

        List<File> sources = opts.getInputLocations();
        assertEquals(2, sources.size());
        assertEquals(new File("foo.x"), sources.get(0));
        assertEquals(new File("bar.x"), sources.get(1));
    }

    @Test
    void testCompilerOptionsBuilder() {
        CompilerOptions opts = new CompilerOptions.Builder()
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

        List<File> modulePath = opts.getModulePath();
        assertEquals(2, modulePath.size());
        assertEquals(new File("/lib1"), modulePath.get(0));
        assertEquals(new File("/lib2"), modulePath.get(1));

        List<File> sources = opts.getInputLocations();
        assertEquals(2, sources.size());
        assertEquals(new File("foo.x"), sources.get(0));
        assertEquals(new File("bar.x"), sources.get(1));
    }

    @Test
    void testCompilerOptionsSerializeToArgs() {
        CompilerOptions opts = new CompilerOptions.Builder()
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

        CompilerOptions opts = CompilerOptions.parse(originalArgs);
        String[] serializedArgs = opts.toCommandLine();
        CompilerOptions reparsed = CompilerOptions.parse(serializedArgs);

        assertEquals(opts.isForcedRebuild(), reparsed.isForcedRebuild());
        assertEquals(opts.getOutputLocation(), reparsed.getOutputLocation());
        assertEquals(opts.getModulePath(), reparsed.getModulePath());
        assertEquals(opts.getInputLocations(), reparsed.getInputLocations());
    }

    @Test
    void testRunnerOptionsParseFromArgs() {
        String[] args = {"-J", "-M", "main", "-I", "key1=value1", "-I", "key2=value2",
                         "-L", "/lib1", "MyModule.xtc", "arg1", "arg2"};

        RunnerOptions opts = RunnerOptions.parse(args);

        assertTrue(opts.isJit());
        assertEquals("main", opts.getMethodName());
        assertFalse(opts.isCompileDisabled());
        assertEquals(new File("MyModule.xtc"), opts.getTarget());

        Map<String, String> injections = opts.getInjections();
        assertEquals(2, injections.size());
        assertEquals("value1", injections.get("key1"));
        assertEquals("value2", injections.get("key2"));

        String[] methodArgs = opts.getMethodArgs();
        assertNotNull(methodArgs);
        assertEquals(2, methodArgs.length);
        assertEquals("arg1", methodArgs[0]);
        assertEquals("arg2", methodArgs[1]);

        List<File> modulePath = opts.getModulePath();
        assertEquals(1, modulePath.size());
        assertEquals(new File("/lib1"), modulePath.get(0));
    }

    @Test
    void testRunnerOptionsBuilderWithDefaults() {
        RunnerOptions opts = RunnerOptions.parse(new String[]{"MyModule.xtc"});

        assertFalse(opts.isJit());
        assertEquals("run", opts.getMethodName()); // Default method name
        assertEquals(new File("MyModule.xtc"), opts.getTarget());
        assertEquals(0, opts.getMethodArgs().length); // Returns empty array, not null
        assertTrue(opts.getInjections().isEmpty());
    }

    @Test
    void testCompilerOptionsImmutability() {
        CompilerOptions opts = new CompilerOptions.Builder()
                .addModulePath(new File("/lib1"))
                .build();

        // Options should be immutable - getModulePath returns unmodifiable list
        assertThrows(UnsupportedOperationException.class, () -> {
            opts.getModulePath().add(new File("bad"));
        });
    }

    @Test
    void testModulePathMultipleForms() {
        // Test 1: Single -L with colon-separated paths (Unix style)
        String sep = File.pathSeparator; // : on Unix, ; on Windows
        String[] args1 = {"-L", "/lib" + sep + "/build" + sep + "/modules", "Test.x"};
        CompilerOptions opts1 = CompilerOptions.parse(args1);
        List<File> paths1 = opts1.getModulePath();
        assertEquals(3, paths1.size());
        assertEquals(new File("/lib"), paths1.get(0));
        assertEquals(new File("/build"), paths1.get(1));
        assertEquals(new File("/modules"), paths1.get(2));

        // Test 2: Multiple -L flags
        String[] args2 = {"-L", "/lib", "-L", "/build", "-L", "/modules", "Test.x"};
        CompilerOptions opts2 = CompilerOptions.parse(args2);
        List<File> paths2 = opts2.getModulePath();
        assertEquals(3, paths2.size());
        assertEquals(new File("/lib"), paths2.get(0));
        assertEquals(new File("/build"), paths2.get(1));
        assertEquals(new File("/modules"), paths2.get(2));

        // Test 3: Combination of both forms
        String[] args3 = {"-L", "/lib" + sep + "/build", "-L", "/modules", "Test.x"};
        CompilerOptions opts3 = CompilerOptions.parse(args3);
        List<File> paths3 = opts3.getModulePath();
        assertEquals(3, paths3.size());
        assertEquals(new File("/lib"), paths3.get(0));
        assertEquals(new File("/build"), paths3.get(1));
        assertEquals(new File("/modules"), paths3.get(2));
    }

    @Test
    void testInvalidOptionDetection() {
        // Apache CLI should throw IllegalArgumentException for unknown options
        assertThrows(IllegalArgumentException.class, () -> {
            CompilerOptions.parse(new String[]{"--invalid-option", "Test.x"});
        });

        assertThrows(IllegalArgumentException.class, () -> {
            RunnerOptions.parse(new String[]{"--bad-flag", "Test.xtc"});
        });
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
        String[] args = {"-I", "key=value", "MyModule.xtc"};
        RunnerOptions opts = RunnerOptions.parse(args);

        // Options should be immutable - getInjections returns unmodifiable map
        assertThrows(UnsupportedOperationException.class, () -> {
            opts.getInjections().put("bad", "value");
        });
    }

    @Test
    void testLongFormFlags() {
        String[] args = {"--rebuild", "--strict", "foo.x"};

        CompilerOptions opts = CompilerOptions.parse(args);

        assertTrue(opts.isForcedRebuild());
        assertTrue(opts.isStrict());
        assertFalse(opts.isNoWarn());
    }

    @Test
    void testNoWarnFlag() {
        String[] args = {"--nowarn", "foo.x"};

        CompilerOptions opts = CompilerOptions.parse(args);

        assertFalse(opts.isStrict());
        assertTrue(opts.isNoWarn());
    }

    @Test
    void testStrictAndNoWarnMutualExclusion() {
        // Test that --strict and --nowarn are mutually exclusive
        String[] args = {"--strict", "--nowarn", "foo.x"};

        assertThrows(IllegalArgumentException.class, () -> {
            CompilerOptions.parse(args);
        });
    }

    @Test
    void testMissingOptionalValues() {
        String[] args = {"foo.x"};

        CompilerOptions opts = CompilerOptions.parse(args);

        assertFalse(opts.isForcedRebuild());
        assertFalse(opts.isStrict());
        assertNull(opts.getOutputLocation());
        assertNull(opts.getVersion());
        assertTrue(opts.getModulePath().isEmpty());
    }

    @Test
    void testEmptyOptions() {
        // Test that empty constructor creates valid empty options
        CompilerOptions opts = new CompilerOptions.Builder().build();

        assertNotNull(opts);
        assertFalse(opts.isForcedRebuild());
        assertTrue(opts.getInputLocations().isEmpty());
        assertTrue(opts.getModulePath().isEmpty());
    }

    // ----- Comprehensive edge case tests -----

    @Test
    void testFlagWithoutValue() {
        String[] args = {"-v", "foo.x"};
        CompilerOptions opts = CompilerOptions.parse(args);
        assertTrue(opts.verbose());
    }

    @Test
    void testFlagWithFileArg() {
        // Apache Commons CLI treats flags as presence/absence
        // Next arg is treated as trailing if not an option
        String[] args = {"-v", "foo.x"};
        CompilerOptions opts = CompilerOptions.parse(args);
        assertTrue(opts.verbose());
        assertEquals(1, opts.getInputLocations().size());
        assertEquals(new File("foo.x"), opts.getInputLocations().get(0));
    }

    @Test
    void testRepeatedMultiArgs() {
        String[] args = {"-L", "/lib1", "-L", "/lib2", "-L", "/lib3", "foo.x"};
        CompilerOptions opts = CompilerOptions.parse(args);

        List<File> modulePath = opts.getModulePath();
        assertEquals(3, modulePath.size());
        assertEquals(new File("/lib1"), modulePath.get(0));
        assertEquals(new File("/lib2"), modulePath.get(1));
        assertEquals(new File("/lib3"), modulePath.get(2));
    }

    @Test
    void testMultipleTrailingArgs() {
        String[] args = {"-v", "file1.x", "file2.x", "file3.x"};
        CompilerOptions opts = CompilerOptions.parse(args);

        List<File> inputs = opts.getInputLocations();
        assertEquals(3, inputs.size());
        assertEquals(new File("file1.x"), inputs.get(0));
        assertEquals(new File("file2.x"), inputs.get(1));
        assertEquals(new File("file3.x"), inputs.get(2));
    }

    @Test
    void testTrailingArgsFollowedByModuleArgs() {
        String[] args = {"-M", "main", "MyModule.xtc", "arg1", "arg2", "arg3"};
        RunnerOptions opts = RunnerOptions.parse(args);

        assertEquals(new File("MyModule.xtc"), opts.getTarget());
        String[] methodArgs = opts.getMethodArgs();
        assertEquals(3, methodArgs.length);
        assertEquals("arg1", methodArgs[0]);
        assertEquals("arg2", methodArgs[1]);
        assertEquals("arg3", methodArgs[2]);
    }

    @Test
    void testMixedShortAndLongFormOptions() {
        String[] args = {"-v", "--rebuild", "-L", "/lib", "--strict", "foo.x"};
        CompilerOptions opts = CompilerOptions.parse(args);

        assertTrue(opts.verbose());
        assertTrue(opts.isForcedRebuild());
        assertTrue(opts.isStrict());
        assertEquals(1, opts.getModulePath().size());
    }

    @Test
    void testUnknownOption() {
        String[] args = {"--unknown", "foo.x"};

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            CompilerOptions.parse(args);
        });
        assertTrue(e.getMessage().contains("Unrecognized option") || e.getMessage().contains("unknown"));
    }

    @Test
    void testMissingRequiredValue() {
        String[] args = {"-o"}; // Missing value for -o

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            CompilerOptions.parse(args);
        });
        assertTrue(e.getMessage().contains("Missing argument") || e.getMessage().contains("requires"));
    }

    @Test
    void testMultipleErrors() {
        String[] args = {"--unknown1", "--unknown2", "foo.x"};

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> {
            CompilerOptions.parse(args);
        });
        // Apache CLI stops at first error
        String message = e.getMessage();
        assertTrue(message.contains("unknown1") || message.contains("Unrecognized"),
            "Expected error in message: " + message);
    }

    @Test
    void testEmptyArgs() {
        String[] args = {};
        CompilerOptions opts = CompilerOptions.parse(args);
        assertNotNull(opts);
        assertTrue(opts.getInputLocations().isEmpty());
    }

    @Test
    void testNullArgs() {
        CompilerOptions opts = CompilerOptions.parse(null);
        assertNotNull(opts);
        assertTrue(opts.getInputLocations().isEmpty());
    }

    @Test
    void testRepeatedInjections() {
        String[] args = {"-I", "key1=value1", "-I", "key2=value2", "-I", "key3=value3", "MyModule.xtc"};
        RunnerOptions opts = RunnerOptions.parse(args);

        Map<String, String> injections = opts.getInjections();
        assertEquals(3, injections.size());
        assertEquals("value1", injections.get("key1"));
        assertEquals("value2", injections.get("key2"));
        assertEquals("value3", injections.get("key3"));
    }

    @Test
    void testInvalidMapValue() {
        // Apache Commons CLI doesn't validate format - we parse and it just won't have '='
        // So the map will be empty or have incorrect parsing
        String[] args = {"-I", "invalidpair", "MyModule.xtc"};

        RunnerOptions opts = RunnerOptions.parse(args);
        // Invalid format means it won't be parsed into the map correctly
        Map<String, String> injections = opts.getInjections();
        assertTrue(injections.isEmpty() || !injections.containsKey("invalidpair"));
    }

    @Test
    void testRepoPathParsing() {
        String pathSeparator = File.pathSeparator;
        String[] args = {"-L", "/lib1" + pathSeparator + "/lib2" + pathSeparator + "/lib3", "foo.x"};
        CompilerOptions opts = CompilerOptions.parse(args);

        List<File> modulePath = opts.getModulePath();
        assertEquals(3, modulePath.size());
        assertEquals(new File("/lib1"), modulePath.get(0));
        assertEquals(new File("/lib2"), modulePath.get(1));
        assertEquals(new File("/lib3"), modulePath.get(2));
    }

    @Test
    void testQuotedStringValue() {
        String[] args = {"--set-version", "1.2.3", "foo.x"};
        CompilerOptions opts = CompilerOptions.parse(args);

        Version version = opts.getVersion();
        assertNotNull(version);
        assertEquals("1.2.3", version.toString());
    }

    @Test
    void testHelpTextGeneration() {
        // Test that help text can be generated for each options type
        CompilerOptions compilerOpts = new CompilerOptions.Builder().build();
        String compilerHelp = compilerOpts.getHelpText();
        assertNotNull(compilerHelp);
        assertTrue(compilerHelp.contains("-L"));
        assertTrue(compilerHelp.contains("-o"));
        assertTrue(compilerHelp.contains("--strict"));

        RunnerOptions runnerOpts = new RunnerOptions.Builder().build();
        String runnerHelp = runnerOpts.getHelpText();
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
        assertThrows(IllegalArgumentException.class, () -> {
            CompilerOptions.parse(new String[]{"-L", "/lib", "--invalid-flag", "foo.x"});
        });

        // Invalid option followed by valid options
        assertThrows(IllegalArgumentException.class, () -> {
            CompilerOptions.parse(new String[]{"--bad-option", "-L", "/lib", "foo.x"});
        });

        // Mix of valid, invalid, and file arguments
        assertThrows(IllegalArgumentException.class, () -> {
            RunnerOptions.parse(new String[]{"-J", "--unknown", "-M", "main", "Test.xtc"});
        });
    }

    @Test
    void testCustomUsageLines() {
        CompilerOptions compilerOpts = new CompilerOptions.Builder().build();
        String compilerUsage = compilerOpts.buildUsageLine("xtc");
        assertTrue(compilerUsage.contains("xtc"));
        assertTrue(compilerUsage.contains("source_files"));

        RunnerOptions runnerOpts = new RunnerOptions.Builder().build();
        String runnerUsage = runnerOpts.buildUsageLine("xec");
        assertTrue(runnerUsage.contains("xec"));
        assertTrue(runnerUsage.contains("module_or_file"));
        assertTrue(runnerUsage.contains("args"));
    }

    @Test
    void testConflictingOptionsStrictAndNowarn() {
        // Test that --strict and --nowarn cannot be used together
        String[] args = {"--strict", "--nowarn", "foo.x"};

        // Should throw IllegalArgumentException due to mutually exclusive options
        assertThrows(IllegalArgumentException.class, () -> CompilerOptions.parse(args),
                "Expected parse to fail when both --strict and --nowarn are specified");
    }

    @Test
    void testStrictOptionAlone() {
        // Test that --strict works when used alone
        String[] args = {"--strict", "foo.x"};
        CompilerOptions opts = CompilerOptions.parse(args);

        assertTrue(opts.isStrict());
        assertFalse(opts.isNoWarn());
    }

    @Test
    void testNowarnOptionAlone() {
        // Test that --nowarn works when used alone
        String[] args = {"--nowarn", "foo.x"};
        CompilerOptions opts = CompilerOptions.parse(args);

        assertFalse(opts.isStrict());
        assertTrue(opts.isNoWarn());
    }

    // Helper
    private boolean contains(String[] array, String value) {
        for (String s : array) {
            if (s.equals(value)) {
                return true;
            }
        }
        return false;
    }
}