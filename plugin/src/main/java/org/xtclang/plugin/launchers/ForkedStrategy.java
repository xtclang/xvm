package org.xtclang.plugin.launchers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.gradle.api.logging.Logger;

import org.xtclang.plugin.XtcRunModule;
import org.xtclang.plugin.tasks.XtcCompileTask;
import org.xtclang.plugin.tasks.XtcLauncherTask;
import org.xtclang.plugin.tasks.XtcRunTask;
import org.xtclang.plugin.tasks.XtcTestTask;

import static org.xtclang.plugin.XtcPluginUtils.failure;
import static org.xtclang.plugin.tasks.XtcLauncherTask.EXIT_CODE_ERROR;

/**
 * Base class for forked (out-of-process) execution strategies.
 * Handles common logic for building ProcessBuilder and executing forked JVM processes.
 */
public abstract class ForkedStrategy implements ExecutionStrategy {

    private static final int STD_BUFFER_SIZE = 8192;
    protected record StreamPlan(boolean copyStdout, boolean copyStderr) {}

    protected final ExecutionMode mode;
    protected final Logger logger;
    protected final String javaExecutable;

    protected ForkedStrategy(final ExecutionMode mode, final Logger logger, final String javaExecutable) {
        this.mode = mode;
        this.logger = logger;
        this.javaExecutable = javaExecutable;
        logger.info("[plugin] [{}] *** launching; javaExecutable from toolchain: {}", mode, javaExecutable);
    }

    @Override
    public ExecutionMode getMode() {
        return this.mode;
    }

    protected LauncherOptionsBuilder optionsBuilder() {
        return new LauncherOptionsBuilder(getMode());
    }

    @Override
    public int execute(final XtcCompileTask task) {
        logger.info("[plugin] execute(XtcCompileTask): {}", getDesc());

        try {
            // Use relative paths for forked mode to preserve build caching
            final var options = optionsBuilder().buildCompilerOptions(task);
            final ProcessBuilder pb = buildProcess(task, options.toCommandLine());
            final StreamPlan streamPlan = configureIO(pb, task);

            final Process process = pb.start();

            // Copy streams if needed (when inheritIO doesn't work due to Gradle redirects)
            // We need to wait for stream copying threads to finish.
            // NOTE: The javaexec task does this automatically, but it is hard to configure it as detachable,
            //   and the ProcessBuilder solution is not Java centric, and is more extensible.
            final var streamThreads = copyProcessStreams(process, streamPlan);
            final int exitCode = waitForProcess(process);
            for (final var thread : streamThreads) {
                thread.join();
            }

            return exitCode;

        } catch (final IOException e) {
            throw failure(e, "[plugin] Failed to start compiler process");
        } catch (final InterruptedException e) {
            logger.error("[plugin] ERROR: Compiler process was interrupted", e);
            Thread.currentThread().interrupt();
            return EXIT_CODE_ERROR;
        }
    }

    @Override
    public int execute(final XtcRunTask task, final XtcRunModule runConfig) {
        logger.info("[plugin] execute(XtcRunTask): {} (runConfig: {})", getDesc(), runConfig);

        try {
            final var moduleName = runConfig.getModuleName().get();
            final var moduleArgs = runConfig.getModuleArgs().get();
            final var options = optionsBuilder().buildRunnerOptions(task, moduleName, moduleArgs);
            final var pb = buildProcess(task, options.toCommandLine());
            final StreamPlan streamPlan = configureIO(pb, task);
            final Process process = pb.start();
            // Copy streams if needed (when inheritIO doesn't work due to Gradle redirects)
            final List<Thread> streamThreads = copyProcessStreams(process, streamPlan);
            final int exitCode = waitForProcess(process);
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

    @Override
    public int execute(final XtcTestTask task, final XtcRunModule runConfig) {
        logger.info("[plugin] execute(XtcTestTask): {} (runConfig: {})", getDesc(), runConfig);

        try {
            final var moduleName = runConfig.getModuleName().get();
            final var moduleArgs = runConfig.getModuleArgs().get();
            final var options = optionsBuilder().buildTestRunnerOptions(task, moduleName, moduleArgs);
            final var pb = buildProcess(task, options.toCommandLine());
            final StreamPlan streamPlan = configureIO(pb, task);
            final Process process = pb.start();
            // Copy streams if needed (when inheritIO doesn't work due to Gradle redirects)
            final List<Thread> streamThreads = copyProcessStreams(process, streamPlan);
            final int exitCode = waitForProcess(process);
            for (final Thread thread : streamThreads) {
                thread.join();
            }
            return exitCode;
        } catch (final IOException e) {
            logger.error("[plugin] Failed to start test runner process", e);
            return -1;
        } catch (final InterruptedException e) {
            logger.error("[plugin] Test runner process was interrupted", e);
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    private ProcessBuilder buildProcess(final XtcLauncherTask<?> task, final String[] programArgs) {
        final var projectDir = task.getProjectDirectory().get().getAsFile();
        final List<String> command = new ArrayList<>();
        command.add(javaExecutable);
        command.addAll(task.getJvmArgs().get());
        command.add("-cp");
        command.add(task.resolveJavaTools().getAbsolutePath());
        command.add(task.getJavaLauncherClassName());
        command.addAll(Arrays.asList(programArgs));
        final ProcessBuilder pb = new ProcessBuilder(command).directory(projectDir);
        if (task.hasVerboseLogging()) {
            logger.lifecycle("[plugin] Forked process command: {}", String.join(" ", command));
        }
        return pb;
    }

    /**
     * Configure I/O for the forked process (inheritIO vs redirect to files).
     * Subclasses override this to implement attached vs detached behavior.
     *
     * @return true if streams should be manually copied (for ATTACHED mode), false otherwise
     */
    protected abstract StreamPlan configureIO(ProcessBuilder pb, XtcLauncherTask<?> task) throws IOException;

    /**
     * Copies process stdout/stderr to System.out/err in background threads.
     * This is necessary when Gradle has redirected file descriptors and inheritIO() doesn't work.
     * Uses byte-buffer based copying (like Gradle's implementation) for better performance and
     * to capture all output including partial lines.
     *
     * @param process the process whose streams to copy
     * @return list of threads copying the streams
     */
    protected List<Thread> copyProcessStreams(final Process process, final StreamPlan streamPlan) {
        record StreamCopy(InputStream input, OutputStream output, String name) {}

        final var streams = new ArrayList<StreamCopy>(2);
        if (streamPlan.copyStdout()) {
            streams.add(new StreamCopy(process.getInputStream(), System.out, "stdout"));
        }
        if (streamPlan.copyStderr()) {
            streams.add(new StreamCopy(process.getErrorStream(), System.err, "stderr"));
        }

        final var threads = new ArrayList<Thread>(streams.size());
        for (final var stream : streams) {
            final var thread = new Thread(() -> {
                try {
                    final byte[] buffer = new byte[STD_BUFFER_SIZE]; // 8KB buffer like Gradle
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
                        logger.warn("[plugin] copyProcessString; problem closing {}: {} (ignored)", stream.name, e.getMessage());
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
     * TODO: Change to execution mode and have excution mode provide toString dsscs and its name()
     */
    protected abstract String getDesc();

    protected static File configuredRedirectFile(final XtcLauncherTask<?> task, final boolean isStdout) {
        final String configuredPath = org.xtclang.plugin.XtcPluginUtils.expandTimestampPlaceholder(
            isStdout ? task.getStdoutPath().get() : task.getStderrPath().get()
        );
        return new File(task.getProjectDirectory().get().getAsFile(), configuredPath);
    }

    protected void ensureParentDirectoryExists(final File file) {
        final File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw failure("failed to create directory for redirected output: {}", parentDir.getAbsolutePath());
        }
    }
}
