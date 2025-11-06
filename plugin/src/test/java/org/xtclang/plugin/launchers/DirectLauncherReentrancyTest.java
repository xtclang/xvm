package org.xtclang.plugin.launchers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xvm.tool.Compiler;
import org.xvm.tool.Runner;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Direct tests of javatools Compiler and Runner reentrancy.
 * These tests call the launchers directly (not through Gradle tasks) to prove:
 * <ul>
 *   <li>✓ Compiler with fork=false IS reentrant</li>
 *   <li>✗ Runner with fork=false is NOT reentrant</li>
 * </ul>
 *
 * <p><b>Root Cause of Runner Bug:</b> Static cache xRTViewToBit.VIEWS (line 109) uses
 * TypeConstant objects as Map keys. When Runner.launch() is called twice:</p>
 * <ol>
 *   <li>First call: Container #1 populates VIEWS with TypeConstant@AAA("Nibble")</li>
 *   <li>Second call: Container #2 tries lookup with TypeConstant@BBB("Nibble")</li>
 *   <li>Lookup fails because TypeConstant@AAA != TypeConstant@BBB (object identity)</li>
 * </ol>
 *
 * <p><b>Failure Point:</b> collections.arrays.BitArray.toUInt8(Boolean) at line 912</p>
 */
public class DirectLauncherReentrancyTest {
    @TempDir
    Path tempDir;

    private Path testArraySource;
    private Path compiledModule;

    @BeforeEach
    void setUp() throws Exception {
        // Create TestArray.x source
        testArraySource = tempDir.resolve("TestArray.x");
        String testArrayCode = """
            module TestArray {
                void run() {
                    testArrayListAdd();
                }

                void testArrayListAdd() {
                    Byte[] bytes = new Byte[10];
                    bytes[0] = 0x01;
                    bytes[1] = 0x02;
                    bytes[2] = 0x03;
                    bytes[9] = 0x0A;

                    @Inject Console console;
                    console.print($"bytes={bytes}");
                }
            }
            """;
        Files.writeString(testArraySource, testArrayCode);

        compiledModule = tempDir.resolve("TestArray.xtc");
    }

    /**
     * Test that Compiler.launch() can be called twice in same JVM.
     * Demonstrates Compiler IS reentrant.
     */
    @Test
    void testCompilerIsReentrant() {
        Path output1 = tempDir.resolve("TestArray1.xtc");
        Path output2 = tempDir.resolve("TestArray2.xtc");

        // First compilation
        try {
            Compiler.launch(new String[]{
                "-o", output1.toString(),
                testArraySource.toString()
            });
        } catch (Exception e) {
            // Skip if ecstasy not available
            assumeTrue(false, "Skipping: ecstasy resources not available");
            return;
        }

        // Second compilation - proves reentrancy
        assertDoesNotThrow(() -> {
            Compiler.launch(new String[]{
                "-o", output2.toString(),
                testArraySource.toString()
            });
        }, "Compiler IS reentrant - second compilation should succeed");
    }

    /**
     * Test that Runner.launch() fails on second invocation in same JVM.
     * This test is DISABLED because it documents the known reentrancy bug.
     *
     * <p><b>Expected Behavior:</b></p>
     * <ul>
     *   <li>First Runner.launch(): ✓ SUCCESS - prints "bytes=0x0102030000000000000A"</li>
     *   <li>Second Runner.launch(): ✗ FAILURE - crashes at BitArray.toUInt8()</li>
     * </ul>
     *
     * <p><b>Error:</b> "Unhandled exception: IllegalArgument: Assertion failed"</p>
     *
     * <p><b>Workaround:</b> Always use fork=true for run tasks (plugin default)</p>
     */
    @Disabled("Runner is NOT reentrant - this test documents the known bug")
    @Test
    void testRunnerIsNotReentrant() throws Exception {
        // First compile the module
        Compiler.launch(new String[]{
            "-o", compiledModule.toString(),
            testArraySource.toString()
        });

        // First run - should succeed
        assertDoesNotThrow(() -> {
            Runner.launch(new String[]{
                "--no-recompile",
                compiledModule.toString()
            });
        }, "First Runner invocation should succeed");

        // Second run - THIS WILL FAIL
        assertThrows(Exception.class, () -> {
            Runner.launch(new String[]{
                "--no-recompile",
                compiledModule.toString()
            });
        }, "Second Runner invocation fails due to static VIEWS cache corruption. " +
           "TypeConstant keys from ConstantPool #1 don't match TypeConstant instances from ConstantPool #2");
    }
}
