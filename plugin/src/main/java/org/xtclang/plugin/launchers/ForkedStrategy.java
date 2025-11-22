package org.xtclang.plugin.launchers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.gradle.api.logging.Logger;

import org.xtclang.plugin.XtcRunModule;
import org.xtclang.plugin.tasks.XtcCompileTask;
import org.xtclang.plugin.tasks.XtcLauncherTask;
import org.xtclang.plugin.tasks.XtcRunTask;

/**
 * Base class for forked (out-of-process) execution strategies.
 * Handles common logic for building ProcessBuilder and executing forked JVM processes.
 */
public abstract class ForkedStrategy<T extends XtcLauncherTask<?>> implements ExecutionStrategy<T> {

    protected final Logger logger;
    protected final String javaExecutable;

    protected ForkedStrategy(final Logger logger, final String javaExecutable) {
        this.logger = logger;
        this.javaExecutable = javaExecutable;
    }

    @Override
    public int execute(final XtcCompileTask task) {
        logger.info("[plugin] {}", getLogMessage());

        try {
            final var options = LauncherOptionsBuilder.buildCompilerOptions(task);
            final ProcessBuilder pb = buildProcess(task, options.toCommandLine());
            final boolean shouldCopyStreams = configureIO(pb, task);

            final Process process = pb.start();

            // Copy streams if needed (when inheritIO doesn't work due to Gradle redirects)
            final var streamThreads = shouldCopyStreams ? copyProcessStreams(process) : null;

            final int exitCode = waitForProcess(process);

            // Wait for stream copying threads to finish
            if (streamThreads != null) {
                for (final var thread : streamThreads) {
                    thread.join();
                }
            }

            return exitCode;

        } catch (final IOException e) {
            logger.error("[plugin] Failed to start compiler process", e);
            return -1;
        } catch (final InterruptedException e) {
            logger.error("[plugin] Compiler process was interrupted", e);
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    @Override
    public int execute(final XtcRunTask task, final XtcRunModule runConfig) {
        logger.info("[plugin] {}", getLogMessage());

        try {
            final String moduleName = runConfig.getModuleName().get();
            final String[] moduleArgs = runConfig.getModuleArgs().get().toArray(String[]::new);
            final var options = LauncherOptionsBuilder.buildRunnerOptions(task, moduleName, moduleArgs);
            final ProcessBuilder pb = buildProcess(task, options.toCommandLine());
            final boolean shouldCopyStreams = configureIO(pb, task);

            final Process process = pb.start();

            // Copy streams if needed (when inheritIO doesn't work due to Gradle redirects)
            final List<Thread> streamThreads = shouldCopyStreams ? copyProcessStreams(process) : Collections.emptyList();

            final int exitCode = waitForProcess(process);

            // Wait for stream copying threads to finish
            for (final Thread thread : streamThreads) {
                thread.join();
            }

            return exitCode;

        } catch (final IOException e) {
            logger.error("[plugin] Failed to start runner process", e);
            return -1;
        } catch (final InterruptedException e) {
            logger.error("[plugin] Runner process was interrupted", e);
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    private ProcessBuilder buildProcess(final XtcLauncherTask<?> task, final String[] programArgs) {
        final var projectDir = task.getProjectDirectory().get().getAsFile();
        final List<String> command = new ArrayList<>();

        // Java executable
        command.add(javaExecutable);

        // JVM arguments
        command.addAll(task.getJvmArgs().get());

        // Classpath (javatools JAR)
        command.add("-cp");
        command.add(task.resolveJavaTools().getAbsolutePath());

        // Main class (XTC compiler or runner)
        command.add(task.getJavaLauncherClassName());

        // Program arguments from options.toCommandLine()
        command.addAll(Arrays.asList(programArgs));

        final ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(projectDir);

        logger.lifecycle("[plugin] Forked process command: {}", String.join(" ", command));

        return pb;
    }

    /**
     * Configure I/O for the forked process (inheritIO vs redirect to files).
     * Subclasses override this to implement attached vs detached behavior.
     *
     * @return true if streams should be manually copied (for ATTACHED mode), false otherwise
     */
    protected abstract boolean configureIO(ProcessBuilder pb, XtcLauncherTask<?> task) throws IOException;

    /**
     * Copies process stdout/stderr to System.out/err in background threads.
     * This is necessary when Gradle has redirected file descriptors and inheritIO() doesn't work.
     * Uses byte-buffer based copying (like Gradle's implementation) for better performance and
     * to capture all output including partial lines.
     *
     * @param process the process whose streams to copy
     * @return list of threads copying the streams
     */
    protected List<Thread> copyProcessStreams(final Process process) {
        record StreamCopy(InputStream input, OutputStream output, String name) {}

        final var streams = List.of(
            new StreamCopy(process.getInputStream(), System.out, "xtc-stdout-copy"),
            new StreamCopy(process.getErrorStream(), System.err, "xtc-stderr-copy")
        );

        final var threads = new ArrayList<Thread>(streams.size());
        for (final var stream : streams) {
            final var thread = new Thread(() -> {
                try {
                    final byte[] buffer = new byte[8192]; // 8KB buffer like Gradle
                    int bytesRead;
                    while ((bytesRead = stream.input.read(buffer)) != -1) {
                        stream.output.write(buffer, 0, bytesRead);
                        stream.output.flush();
                    }
                } catch (final IOException e) {
                    logger.warn("[plugin] Error copying {}: {}", stream.name, e.getMessage());
                } finally {
                    try {
                        stream.input.close();
                    } catch (final IOException e) {
                        // Ignore close errors
                    }
                }
            }, stream.name);
            thread.start();
            threads.add(thread);
        }
        return threads;
    }

    /**
     * Wait for process completion and return exit code.
     * Subclasses override for detached processes that don't wait.
     */
    protected int waitForProcess(final Process process) throws InterruptedException {
        return process.waitFor();
    }

    /**
     * Get the log message describing this strategy.
     */
    protected abstract String getLogMessage();
}