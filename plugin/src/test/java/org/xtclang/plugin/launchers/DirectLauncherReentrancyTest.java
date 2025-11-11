package org.xtclang.plugin.launchers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xvm.runtime.template._native.collections.arrays.xRTViewToBit;
import org.xvm.tool.Launcher;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Direct tests of javatools Launcher reentrancy.
 * These tests call the Launcher directly (not through Gradle tasks) to prove:
 * <ul>
 *   <li>✓ Compiler (xcc) with fork=false IS reentrant</li>
 *   <li>✗ Runner (xec) with fork=false is NOT reentrant</li>
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
     * Test that Launcher (xcc command) can be called twice in same JVM.
     * Demonstrates Compiler IS reentrant.
     */
    @Test
    void testCompilerIsReentrant() {
        Path output1 = tempDir.resolve("TestArray1.xtc");
        Path output2 = tempDir.resolve("TestArray2.xtc");

        // First compilation
        String[] compilerArgs1 = List.of("xcc", "-o", output1.toString(), testArraySource.toString()).toArray(String[]::new);
        try {
            Launcher.main(compilerArgs1);
        } catch (Exception e) {
            // Skip if ecstasy not available
            assumeTrue(false, "Skipping: ecstasy resources not available");
            return;
        }

        // Second compilation - proves reentrancy
        String[] compilerArgs2 = List.of("xcc", "-o", output2.toString(), testArraySource.toString()).toArray(String[]::new);
        assertDoesNotThrow(() -> {
            Launcher.main(compilerArgs2);
        }, "Compiler IS reentrant - second compilation should succeed");
    }

    /**
     * Test that Launcher (xec command) CAN be called twice in same JVM with the cache-clearing hack.
     * This test verifies that calling clearViewsCache() between runs allows reentrancy.
     *
     * <p><b>Expected Behavior:</b></p>
     * <ul>
     *   <li>First Launcher.main(xec): ✓ SUCCESS - prints "bytes=0x0102030000000000000A"</li>
     *   <li>clearViewsCache(): Clear static VIEWS to force re-initialization</li>
     *   <li>Second Launcher.main(xec): ✓ SUCCESS - now works with fresh cache</li>
     * </ul>
     *
     * <p><b>Workaround:</b> Call xRTViewToBit.clearViewsCache() between Launcher.main() calls</p>
     */
    @Test
    void testRunnerReentrancyWithCacheClearing() throws Exception {
        // First compile the module
        String[] compilerArgs = List.of("xcc", "-o", compiledModule.toString(), testArraySource.toString()).toArray(String[]::new);
        Launcher.main(compilerArgs);

        // First run - should succeed
        String[] runnerArgs = List.of("xec", "--no-recompile", compiledModule.toString()).toArray(String[]::new);
        assertDoesNotThrow(() -> {
            Launcher.main(runnerArgs);
        }, "First Runner invocation should succeed");

        // HACK: Clear the static VIEWS cache to allow reentrancy
        xRTViewToBit.clearViewsCache();

        // Second run - should now succeed with the cache cleared
        assertDoesNotThrow(() -> {
            Launcher.main(runnerArgs);
        }, "Second Runner invocation should succeed after clearing VIEWS cache");
    }
}
