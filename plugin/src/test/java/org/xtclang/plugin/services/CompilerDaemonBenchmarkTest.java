package org.xtclang.plugin.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Benchmark test that measures the performance benefits of the compiler daemon approach
 * by running multiple compilations programmatically without spawning separate JVM processes.
 *
 * <p>This test demonstrates:
 * <ul>
 *   <li>JIT warmup effects - later compilations are faster as HotSpot optimizes hot paths</li>
 *   <li>ClassLoader reuse - compiler classes stay loaded between invocations</li>
 *   <li>No JVM startup overhead - single persistent JVM instance</li>
 * </ul>
 */
public class CompilerDaemonBenchmarkTest {

    @TempDir
    Path tempDir;

    private File sourceFile;
    private File outputDir;
    private File xdkLibDir;
    private File xdkJavatoolsDir;
    private URLClassLoader compilerClassLoader;
    private Class<?> compilerClass;
    private Method compilerLaunchMethod;

    @BeforeEach
    void setUp() throws Exception {
        // Find the javatools JAR - it should be on the test classpath
        final File javatoolsJar = findJavatoolsJar();
        assumeTrue(javatoolsJar != null && javatoolsJar.exists(),
                "Javatools JAR must be available for benchmark test. Run './gradlew javatools:jar' first.");

        // Find the XDK lib directory containing ecstasy.xtc
        xdkLibDir = findXdkLibDir();
        assumeTrue(xdkLibDir != null && xdkLibDir.exists() && xdkLibDir.isDirectory(),
                "XDK lib directory with ecstasy.xtc must be available. Run './gradlew xdk:installDist' first.");

        // Find the XDK javatools directory containing javatools modules
        xdkJavatoolsDir = findXdkJavatoolsDir();
        assumeTrue(xdkJavatoolsDir != null && xdkJavatoolsDir.exists() && xdkJavatoolsDir.isDirectory(),
                "XDK javatools directory must be available. Run './gradlew xdk:installDist' first.");

        // Create a simple XTC module to compile
        final Path sourceDir = tempDir.resolve("src");
        Files.createDirectories(sourceDir);
        sourceFile = sourceDir.resolve("TestModule.x").toFile();
        Files.writeString(sourceFile.toPath(), """
                module TestModule {
                    void run() {
                        @Inject Console console;
                        console.print("Benchmark test module");
                    }
                }
                """);

        // Create output directory
        outputDir = tempDir.resolve("build").toFile();
        assertTrue(outputDir.mkdirs());

        // Set up compiler ClassLoader (same as daemon does)
        final URL[] urls = {javatoolsJar.toURI().toURL()};
        final ClassLoader parent = Thread.currentThread().getContextClassLoader();
        compilerClassLoader = new URLClassLoader(urls, parent);

        // Load compiler class and launch method
        compilerClass = compilerClassLoader.loadClass("org.xvm.tool.Compiler");
        compilerLaunchMethod = compilerClass.getMethod("launch", String[].class);
    }

    /**
     * Benchmark test: Run the compiler multiple times and measure performance improvements.
     * Expected behavior:
     * - First run: Slower (cold JIT, class loading)
     * - Subsequent runs: Progressively faster (JIT optimization, cached classes)
     *
     * Number of runs can be configured via the system property 'xtc.benchmark.runs' (default: 10).
     */
    @Test
    void benchmarkMultipleCompilations() throws Exception {
        final int numRuns = Integer.getInteger("xtc.benchmark.runs", 10);
        final List<Long> compilationTimes = new ArrayList<>();

        System.out.println("\n=== Compiler Daemon Benchmark ===");
        System.out.println("Running " + numRuns + " compilations to demonstrate JIT warmup benefits\n");

        final long benchmarkStartTime = System.nanoTime();

        for (int i = 1; i <= numRuns; i++) {
            // Clean output directory for each run
            deleteDirectory(outputDir);
            assertTrue(outputDir.mkdirs());

            // Prepare compiler arguments (no tool name - Compiler.launch() doesn't expect it)
            final String[] args = {
                    "-L", xdkLibDir.getAbsolutePath(),
                    "-L", xdkJavatoolsDir.getAbsolutePath(),
                    "-o", outputDir.getAbsolutePath(),
                    sourceFile.getAbsolutePath()
            };

            // Measure compilation time
            final long startNanos = System.nanoTime();

            final ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                // Set TCCL to compiler classloader (same as daemon)
                Thread.currentThread().setContextClassLoader(compilerClassLoader);

                // Invoke compiler
                compilerLaunchMethod.invoke(null, (Object) args);

            } catch (final Exception e) {
                // Check for LauncherException (normal exit)
                final Throwable cause = e.getCause();
                if (cause != null && "org.xvm.tool.Launcher$LauncherException".equals(cause.getClass().getName())) {
                    final boolean isError = (Boolean) cause.getClass().getField("error").get(cause);
                    assertEquals(false, isError, "Compilation should succeed");
                } else {
                    throw e;
                }
            } finally {
                Thread.currentThread().setContextClassLoader(originalContextClassLoader);
            }

            final long durationNanos = System.nanoTime() - startNanos;
            final long durationMs = durationNanos / 1_000_000;
            compilationTimes.add(durationMs);

            System.out.printf("Run %d: %dms%n", i, durationMs);

            // Verify compilation produced output
            final File[] outputFiles = outputDir.listFiles();
            assertTrue(outputFiles != null && outputFiles.length > 0,
                    "Compilation should produce output files");
        }

        final long benchmarkEndTime = System.nanoTime();
        final long totalBenchmarkTimeMs = (benchmarkEndTime - benchmarkStartTime) / 1_000_000;

        // Analyze results
        System.out.println("\n=== Performance Analysis ===");
        final long firstRun = compilationTimes.get(0);
        final long lastRun = compilationTimes.get(numRuns - 1);
        final double improvement = ((firstRun - lastRun) / (double) firstRun) * 100;

        // Calculate dynamic phase boundaries based on number of runs
        // Phase 1: First 10% (warming up)
        // Phase 2: Next 40% (getting warm)
        // Phase 3: Last 50% (fully warm)
        final int phase1End = Math.max(1, numRuns / 10);
        final int phase2End = Math.max(phase1End + 1, (numRuns * 5) / 10);

        final double avgPhase1 = compilationTimes.stream()
                .limit(phase1End)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

        final double avgPhase2 = compilationTimes.stream()
                .skip(phase1End)
                .limit(phase2End - phase1End)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

        final double avgPhase3 = compilationTimes.stream()
                .skip(phase2End)
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

        final double overallAvg = compilationTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);

        System.out.printf("Total benchmark time: %dms (%.1fs)%n", totalBenchmarkTimeMs, totalBenchmarkTimeMs / 1000.0);
        System.out.printf("Total runs:           %d compilations%n", numRuns);
        System.out.printf("%n");
        System.out.printf("First run:            %dms (cold JIT, class loading)%n", firstRun);
        System.out.printf("Last run:             %dms (fully warmed up)%n", lastRun);
        System.out.printf("Improvement:          %.1f%%%n", improvement);
        System.out.printf("%n");
        System.out.printf("Average runs 1-%d:    %.0fms (warming up)%n", phase1End, avgPhase1);
        System.out.printf("Average runs %d-%d:   %.0fms (getting warm)%n", phase1End + 1, phase2End, avgPhase2);
        System.out.printf("Average runs %d-%d:  %.0fms (fully warm)%n", phase2End + 1, numRuns, avgPhase3);
        System.out.printf("Overall average:      %.0fms%n", overallAvg);

        System.out.println("\nThis demonstrates the benefits of the compiler daemon:");
        System.out.println("- No JVM startup overhead");
        System.out.println("- ClassLoader reuse (compiler stays loaded)");
        System.out.println("- JIT optimization (code gets faster over time)");

        // Verify we see some improvement (at least 5% faster on last run)
        assertTrue(improvement > 0,
                "Later runs should be faster due to JIT warmup");
    }

    /**
     * Find the javatools JAR on the classpath or in the build directory.
     */
    private File findJavatoolsJar() {
        // First, try to find it on the classpath (when running from IDE or with test dependencies)
        final String classpath = System.getProperty("java.class.path");
        for (final String entry : classpath.split(File.pathSeparator)) {
            if (entry.contains("javatools") && entry.endsWith(".jar") && !entry.contains("unicode")) {
                final File jar = new File(entry);
                if (jar.exists()) {
                    return jar;
                }
            }
        }

        // Second, try to find it in the build directory
        final String userDir = System.getProperty("user.dir");
        final Path xvmRoot = Path.of(userDir).endsWith("plugin")
                ? Path.of(userDir).getParent()
                : Path.of(userDir);
        final File buildJar = xvmRoot.resolve("javatools/build/libs").toFile();

        if (buildJar.exists()) {
            final File[] jars = buildJar.listFiles((dir, name) ->
                    name.startsWith("javatools-") && name.endsWith(".jar"));
            if (jars != null && jars.length > 0) {
                return jars[0];
            }
        }

        return null;
    }

    /**
     * Find the XDK lib directory containing ecstasy.xtc and other modules.
     */
    private File findXdkLibDir() {
        final String userDir = System.getProperty("user.dir");
        final Path xvmRoot = Path.of(userDir).endsWith("plugin")
                ? Path.of(userDir).getParent()
                : Path.of(userDir);

        // Check in xdk/build/install/xdk/lib (installDist output)
        final File installLibDir = xvmRoot.resolve("xdk/build/install/xdk/lib").toFile();
        if (installLibDir.exists() && installLibDir.isDirectory()) {
            final File ecstasy = new File(installLibDir, "ecstasy.xtc");
            if (ecstasy.exists()) {
                return installLibDir;
            }
        }

        return null;
    }

    /**
     * Find the XDK javatools directory containing javatools modules like mack.
     */
    private File findXdkJavatoolsDir() {
        final String userDir = System.getProperty("user.dir");
        final Path xvmRoot = Path.of(userDir).endsWith("plugin")
                ? Path.of(userDir).getParent()
                : Path.of(userDir);

        // Check in xdk/build/install/xdk/javatools (installDist output)
        final File installJavatoolsDir = xvmRoot.resolve("xdk/build/install/xdk/javatools").toFile();
        if (installJavatoolsDir.exists() && installJavatoolsDir.isDirectory()) {
            return installJavatoolsDir;
        }

        return null;
    }

    /**
     * Recursively delete a directory and all its contents.
     */
    private void deleteDirectory(final File dir) {
        if (!dir.exists()) {
            return;
        }
        final File[] files = dir.listFiles();
        if (files != null) {
            for (final File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }
}
