package org.xtclang.plugin.services;

import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.launchers.CommandLine;
import org.xtclang.plugin.launchers.XtcExecResult;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A build service that maintains a persistent XTC compiler instance across all compilation tasks
 * in the build. This eliminates the overhead of repeatedly spawning new Java processes for each
 * compilation, significantly improving build performance.
 *
 * <p>The service:
 * <ul>
 *   <li>Creates a single ClassLoader with the XTC compiler and its dependencies</li>
 *   <li>Reuses this ClassLoader across all compilation tasks in the build</li>
 *   <li>Thread-safe: synchronizes access to the compiler</li>
 *   <li>Automatically cleaned up when the build finishes</li>
 * </ul>
 *
 * <p><b>Performance Benefits:</b>
 * <ul>
 *   <li>No JVM startup overhead for each compilation</li>
 *   <li>ClassLoader and class metadata reused across compilations</li>
 *   <li>JIT compilation benefits from warmed-up code</li>
 * </ul>
 */
public abstract class XtcCompilerService implements BuildService<XtcCompilerService.Params>, AutoCloseable {

    public interface Params extends BuildServiceParameters {
        // No parameters needed currently - compiler classpath is provided per-compilation
    }

    private final ReentrantLock compilationLock = new ReentrantLock();
    private volatile URLClassLoader compilerClassLoader;
    private volatile Class<?> compilerClass;
    private volatile Method compilerMainMethod;

    /**
     * Compile XTC source using the persistent compiler daemon.
     *
     * @param commandLine The command line arguments for the compiler
     * @param classpath The classpath containing the XTC compiler and dependencies
     * @param logger Logger for diagnostic output
     * @return The execution result
     */
    public XtcExecResult compile(
            @NotNull final CommandLine commandLine,
            @NotNull final FileCollection classpath,
            @NotNull final Logger logger) {

        compilationLock.lock();
        try {
            // Lazy initialization of compiler on first use
            if (compilerClassLoader == null) {
                initializeCompiler(classpath, logger);
            }

            return invokeCompiler(commandLine, logger);

        } catch (final Exception e) {
            logger.error("[compiler-daemon] Compilation failed with exception", e);
            final var builder = XtcExecResult.builder(XtcCompilerService.class, commandLine);
            builder.exitValue(-1);
            builder.failure(e);
            return builder.build();
        } finally {
            compilationLock.unlock();
        }
    }

    private void initializeCompiler(@NotNull final FileCollection classpath, @NotNull final Logger logger) throws Exception {
        logger.lifecycle("[compiler-daemon] Initializing XTC compiler daemon...");

        // Convert FileCollection to URL array for ClassLoader
        final URL[] urls = classpath.getFiles().stream()
                .map(File::toURI)
                .map(uri -> {
                    try {
                        return uri.toURL();
                    } catch (final Exception e) {
                        throw new RuntimeException("Failed to convert classpath entry to URL: " + uri, e);
                    }
                })
                .toArray(URL[]::new);

        // Create isolated ClassLoader for the compiler
        // Use null parent to isolate from Gradle's ClassLoader and avoid conflicts
        this.compilerClassLoader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());

        // Load compiler class
        this.compilerClass = compilerClassLoader.loadClass("org.xvm.tool.Compiler");

        // Get the launch method that accepts String[] args and throws LauncherException
        // (NOT main(), which would call System.exit() and kill the daemon)
        this.compilerMainMethod = compilerClass.getMethod("launch", String[].class);

        logger.lifecycle("[compiler-daemon] XTC compiler daemon initialized successfully");
        logger.info("[compiler-daemon] Compiler class: {}", compilerClass.getName());
        logger.info("[compiler-daemon] ClassLoader: {}", compilerClassLoader);
    }

    private XtcExecResult invokeCompiler(@NotNull final CommandLine commandLine, @NotNull final Logger logger) {
        try {
            logger.info("[compiler-daemon] Invoking compiler with args: {}", commandLine.toString());

            // The XTC Compiler.main() method expects the first argument to be the command name
            // which it ignores, so we include it
            final String[] args = commandLine.toList().toArray(new String[0]);

            // Save and redirect System.out/err to capture compiler output
            // (The XTC compiler writes to System.out/err)
            final var originalOut = System.out;
            final var originalErr = System.err;

            try {
                // Invoke the compiler's main method
                // Note: This runs synchronously in the current thread
                compilerMainMethod.invoke(null, (Object) args);

                // If we get here, compilation succeeded
                final var builder = XtcExecResult.builder(XtcCompilerService.class, commandLine);
                builder.exitValue(0);
                return builder.build();

            } finally {
                // Restore original streams
                System.setOut(originalOut);
                System.setErr(originalErr);
            }

        } catch (final Exception e) {
            // Check if this is a LauncherException (expected exit request from compiler)
            final Throwable cause = e.getCause();
            if (cause != null && cause.getClass().getName().equals("org.xvm.tool.Launcher$LauncherException")) {
                try {
                    // Extract error flag from LauncherException
                    final boolean isError = (Boolean) cause.getClass().getField("error").get(cause);
                    final int exitCode = isError ? 1 : 0;
                    logger.info("[compiler-daemon] Compiler completed with exit code: {}", exitCode);

                    final var builder = XtcExecResult.builder(XtcCompilerService.class, commandLine);
                    builder.exitValue(exitCode);
                    if (exitCode != 0) {
                        builder.failure(cause);
                    }
                    return builder.build();
                } catch (final Exception extractError) {
                    logger.error("[compiler-daemon] Failed to extract error flag from LauncherException", extractError);
                }
            }

            // Unexpected error
            logger.error("[compiler-daemon] Compiler invocation failed with unexpected exception", e);
            final var builder = XtcExecResult.builder(XtcCompilerService.class, commandLine);
            builder.exitValue(-1);
            builder.failure(e);
            return builder.build();
        }
    }

    @Override
    public void close() {
        if (compilerClassLoader != null) {
            try {
                compilerClassLoader.close();
                // Note: We can't log here as we don't have access to a logger in close()
                // The service will be cleaned up silently when the build finishes
            } catch (final Exception e) {
                // Silently handle cleanup errors
                e.printStackTrace(System.err);
            }
        }
    }
}