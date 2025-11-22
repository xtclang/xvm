package org.xvm.tool;

import org.xvm.asm.ErrorListener;

/**
 * Interface for XTC tools that can be launched programmatically.
 * This interface enables ServiceLoader-based discovery and eliminates the need
 * for reflection-based bridge classes.
 *
 * <p>Implementations of this interface can be discovered at runtime using
 * {@link java.util.ServiceLoader}, allowing external tools (like Gradle plugins)
 * to invoke XTC launchers without direct compile-time dependencies.
 *
 * <p>To make an implementation discoverable, create a file at:
 * <pre>
 * META-INF/services/org.xvm.tool.Launchable
 * </pre>
 * containing the fully qualified class name(s) of the implementation(s).
 *
 * <p>Example usage from a Gradle plugin:
 * <pre>
 * // Load javatools.jar dynamically
 * URLClassLoader classLoader = new URLClassLoader(
 *     new URL[] { javaToolsJar.toURI().toURL() },
 *     getClass().getClassLoader()
 * );
 *
 * // Discover Launchable via ServiceLoader
 * Launchable launcher = ServiceLoader.load(Launchable.class, classLoader)
 *     .iterator()
 *     .next();
 *
 * // Build options programmatically
 * CompilerOptions options = CompilerOptions.builder()
 *     .addInputFile("MyModule.x")
 *     .setOutputLocation("build/")
 *     .build();
 *
 * // Launch with custom console/error listener
 * int exitCode = launcher.launch(options, customConsole, customErrorListener);
 * </pre>
 */
@FunctionalInterface
public interface Launchable {
    /**
     * Launch the tool with the given options, console, and error listener.
     *
     * <p>This method provides programmatic access to the XTC launcher with full
     * control over I/O (via Console) and error handling (via ErrorListener).
     * This enables IDE integration, language servers, and custom build tools.
     *
     * @param options Configuration options (CompilerOptions, RunnerOptions, etc.)
     * @param console Console for I/O (null uses default console)
     * @param errorListener ErrorListener for compilation errors (null uses BLACKHOLE)
     * @return Exit code (0 for success, non-zero for error)
     */
    int launch(LauncherOptions options, Console console, ErrorListener errorListener);
}
