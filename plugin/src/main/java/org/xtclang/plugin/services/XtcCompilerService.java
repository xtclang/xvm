package org.xtclang.plugin.services;

import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.Logger;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcJavaToolsRuntime;
import org.xtclang.plugin.launchers.CommandLine;
import org.xtclang.plugin.launchers.XtcExecResult;

import org.xvm.asm.ErrorListener.ErrorInfo;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
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
public abstract class XtcCompilerService implements BuildService<XtcCompilerService.@NotNull Params>, AutoCloseable {

    static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public interface Params extends BuildServiceParameters {
        // No parameters needed currently - compiler classpath is provided per-compilation
    }

    /**
     * Records a single compilation invocation in the daemon's history.
     */
    private record CompilationHistoryEntry(Instant timestamp, String commandLine, int exitCode, long durationMs) {
        @Override
            public @NotNull String toString() {
                return String.format("[%s] exit=%d, duration=%dms, cmd=%s", FORMATTER.format(timestamp), exitCode, durationMs, commandLine);
            }
        }
    /**
     * Captures compiler errors and warnings during compilation.
     * This class bridges between the plugin classloader and the compiler classloader
     * using a dynamic proxy to implement the ErrorListener interface from the compiler's classloader.
     */
    private static class CompilerErrorCollector {
        private final List<ErrorInfo> errors = new ArrayList<>();
        private final Logger logger;

        public CompilerErrorCollector(@NotNull final Logger logger) {
            this.logger = logger;
        }

        /**
         * Creates a dynamic proxy that implements the ErrorListener interface from the compiler classloader.
         * This allows us to bridge between the two classloaders.
         */
        public Object createProxyListener(@NotNull final ClassLoader compilerClassLoader) throws Exception {
                final Class<?> errorListenerClass = compilerClassLoader.loadClass("org.xvm.asm.ErrorListener");
                final Class<?> errorInfoClass = compilerClassLoader.loadClass("org.xvm.asm.ErrorListener$ErrorInfo");
                final Class<?> severityClass = compilerClassLoader.loadClass("org.xvm.util.Severity");

            return Proxy.newProxyInstance(
                    compilerClassLoader,
                    new Class<?>[]{errorListenerClass}, (proxy, method, args) -> {
                        if ("log".equals(method.getName()) && args != null && args.length == 1 && errorInfoClass.isInstance(args[0])) {
                            // Extract error information from ErrorInfo
                            final Object errorInfo = args[0];
                            final Method getSeverity = errorInfoClass.getMethod("getSeverity");
                            final Method getMessage = errorInfoClass.getMethod("getMessage");

                            final Object severity = getSeverity.invoke(errorInfo);
                            final String message = (String) getMessage.invoke(errorInfo);

                            // Log to Gradle logger
                            final Method getSeverityName = severityClass.getMethod("name");
                            final String severityName = (String) getSeverityName.invoke(severity);

                            logger.lifecycle("[compiler-error] {}: {}", severityName, message);

                            // Return false to continue compilation
                            return false;
                        }

                        // Default implementation for other methods
                        if ("isAbortDesired".equals(method.getName())) {
                            return false;
                        }
                        if ("hasSeriousErrors".equals(method.getName())) {
                            return false;
                        }
                        if ("hasError".equals(method.getName())) {
                            return false;
                        }
                        if ("isSilent".equals(method.getName())) {
                            return false;
                        }

                        return null;
                    });
        }

        public List<ErrorInfo> getErrors() {
            return new ArrayList<>(errors);
        }
    }

    private final ReentrantLock compilationLock = new ReentrantLock();
    private final List<CompilationHistoryEntry> compilationHistory = new ArrayList<>();
    private volatile URLClassLoader compilerClassLoader;
    private volatile Class<?> compilerClass;
    private volatile Method compilerMainMethod;
    private volatile Constructor<?> compilerConstructor;
    private volatile List<File> currentClasspath;

    /**
     * Compile XTC source using the persistent compiler daemon.
     *
     * @param commandLine The command line arguments for the compiler
     * @param classpath The classpath containing the XTC compiler and dependencies
     * @param workingDirectory The working directory for resolving relative paths
     * @param logger Logger for diagnostic output
     * @return The execution result
     */
    public XtcExecResult compile(
            @NotNull final CommandLine commandLine,
            @NotNull final FileCollection classpath,
            @NotNull final File workingDirectory,
            @NotNull final Logger logger) {

        compilationLock.lock();
        try {
            // Check if we need to (re)initialize the compiler
            final List<File> newClasspath = classpath.getFiles().stream().sorted().toList();
            final boolean classpathChanged = !newClasspath.equals(currentClasspath);

            if (compilerClassLoader == null) {
                logger.lifecycle("[compiler-daemon] Compiler classloader initialized");
                initializeCompiler(classpath, logger);
                currentClasspath = newClasspath;
            } else if (classpathChanged) {
                logger.lifecycle("[compiler-daemon] Classpath changed - reinitializing compiler daemon");
                logger.lifecycle("[compiler-daemon] Old classpath: {}", currentClasspath);
                logger.lifecycle("[compiler-daemon] New classpath: {}", newClasspath);
                closeCompiler();
                initializeCompiler(classpath, logger);
                currentClasspath = newClasspath;
                // Clear history since we have a new compiler instance
                compilationHistory.clear();
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

        // Extract javatools.jar from classpath (should be the only file for compiler daemon)
        final List<File> classpathFiles = new ArrayList<>(classpath.getFiles());
        if (classpathFiles.isEmpty()) {
            throw new RuntimeException("No javatools.jar found in classpath");
        }

        // For now, we expect just javatools.jar, but support multiple files for flexibility
        if (classpathFiles.size() == 1) {
            // Use XtcJavaToolsRuntime for consistent classloader creation and logging
            final File javaToolsJar = classpathFiles.getFirst();
            this.compilerClassLoader = XtcJavaToolsRuntime.createJavaToolsClassLoader(javaToolsJar, logger);
        } else {
            // Multiple files - create URLClassLoader manually
            logger.warn("[compiler-daemon] Multiple classpath entries detected, expected only javatools.jar");
            final URL[] urls = classpathFiles.stream()
                    .map(File::toURI)
                    .map(uri -> {
                        try {
                            return uri.toURL();
                        } catch (final Exception e) {
                            throw new RuntimeException("Failed to convert classpath entry to URL: " + uri, e);
                        }
                    })
                    .toArray(URL[]::new);

            final ClassLoader parentClassLoader = Thread.currentThread().getContextClassLoader();
            this.compilerClassLoader = new URLClassLoader(urls, parentClassLoader);
        }

        // Load compiler class but don't force initialization yet
        // Let static initializers run naturally when the class is first used
        this.compilerClass = compilerClassLoader.loadClass("org.xvm.tool.Compiler");

        // Get the launch method that accepts String[] args and throws LauncherException
        // (NOT main(), which would call System.exit() and kill the daemon)
        this.compilerMainMethod = compilerClass.getMethod("launch", String[].class);

        logger.lifecycle("[compiler-daemon] XTC compiler daemon initialized successfully");
        logger.info("[compiler-daemon] Compiler class: {}", compilerClass.getName());
        logger.info("[compiler-daemon] ClassLoader: {}", compilerClassLoader);
    }

    private XtcExecResult invokeCompiler(@NotNull final CommandLine commandLine, @NotNull final Logger logger) {
        final Instant startTime = Instant.now();
        int exitCode = -1;

        try {
            // Print compilation history before this invocation
            printHistory(logger);

            logger.info("[compiler-daemon] Invoking compiler with args: {}", commandLine);

            // The XTC Compiler.main() method expects the first argument to be the command name
            // which it ignores, so we include it
            final String[] args = commandLine.toList().toArray(new String[0]);

            // Save and redirect System.out/err to capture compiler output
            // (The XTC compiler writes to System.out/err)
            final var originalOut = System.out;
            final var originalErr = System.err;
            final ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();

            try {
                // Set the thread context classloader to the compiler classloader
                // This ensures ResourceBundle.getBundle() and other resource loading uses
                // the compiler classloader, which has TCCL as parent for JDK module access
                Thread.currentThread().setContextClassLoader(compilerClassLoader);

                // Invoke the compiler's main method
                // Note: The compiler classloader uses TCCL as parent, so it has access to both
                // the compiler classes AND the platform/builtin loaders for JDK resources
                compilerMainMethod.invoke(null, (Object) args);

                // If we get here, compilation succeeded
                exitCode = 0;
                final var builder = XtcExecResult.builder(XtcCompilerService.class, commandLine);
                builder.exitValue(exitCode);
                return builder.build();

            } finally {
                // Restore original context classloader
                Thread.currentThread().setContextClassLoader(originalContextClassLoader);
                // Restore original streams
                System.setOut(originalOut);
                System.setErr(originalErr);
            }

        } catch (final Exception e) {
            // Check if this is a LauncherException (expected exit request from compiler)
            final Throwable cause = e.getCause();
            if (cause != null && "org.xvm.tool.Launcher$LauncherException".equals(cause.getClass().getName())) {
                try {
                    // Extract error flag from LauncherException
                    final boolean isError = (Boolean) cause.getClass().getField("error").get(cause);
                    exitCode = isError ? 1 : 0;
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
            exitCode = -1;
            logger.error("[compiler-daemon] Compiler invocation failed with unexpected exception", e);
            final var builder = XtcExecResult.builder(XtcCompilerService.class, commandLine);
            builder.exitValue(exitCode);
            builder.failure(e);
            return builder.build();
        } finally {
            // Record this compilation in history
            final long durationMs = java.time.Duration.between(startTime, Instant.now()).toMillis();
            recordCompilation(startTime, commandLine.toString(), exitCode, durationMs);
        }
    }

    /**
     * Records a compilation in the daemon's history.
     */
    private void recordCompilation(final Instant timestamp, final String commandLine, final int exitCode, final long durationMs) {
        compilationHistory.add(new CompilationHistoryEntry(timestamp, commandLine, exitCode, durationMs));
    }

    /**
     * Prints the compilation history to the logger.
     */
    private void printHistory(@NotNull final Logger logger) {
        if (compilationHistory.isEmpty()) {
            logger.lifecycle("[compiler-daemon] History: No previous compilations in this daemon instance");
            return;
        }
        logger.lifecycle("[compiler-daemon] History: {} previous compilation(s) in this daemon instance:", compilationHistory.size());
        for (int i = 0; i < compilationHistory.size(); i++) {
            logger.lifecycle("[compiler-daemon]   {}. {}", i + 1, compilationHistory.get(i));
        }
    }

    /**
     * Closes the current compiler ClassLoader.
     * Called when classpath changes or during service shutdown.
     */
    private void closeCompiler() {
        if (compilerClassLoader != null) {
            try {
                compilerClassLoader.close();
                compilerClassLoader = null;
                compilerClass = null;
                compilerMainMethod = null;
            } catch (final Exception e) {
                // Log to stderr since we may not have a logger in all contexts
                e.printStackTrace(System.err);
            }
        }
    }

    @Override
    public void close() {
        closeCompiler();
    }
}
