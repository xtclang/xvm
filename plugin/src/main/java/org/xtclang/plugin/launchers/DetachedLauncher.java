package org.xtclang.plugin.launchers;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.process.ExecResult;

/**
 * Base class for launchers that run processes in detached mode.
 * Provides common functionality for starting background processes and managing their lifecycle.
 */
public abstract class DetachedLauncher {

    /**
     * Mock ExecResult that indicates successful process start (exit value 0).
     * Reusable since it's stateless.
     */
    private static final ExecResult MOCK_SUCCESS_RESULT = new ExecResult() {
        @Override
        public int getExitValue() {
            return 0;
        }

        @Override
        public ExecResult assertNormalExitValue() throws org.gradle.process.internal.ExecException {
            return this;
        }

        @Override
        public ExecResult rethrowFailure() throws org.gradle.process.internal.ExecException {
            return this;
        }
    };

    protected final Logger logger;
    protected final File buildDir;
    protected final File projectDir;

    protected DetachedLauncher(final Logger logger, final File buildDir, final File projectDir) {
        this.logger = logger;
        this.buildDir = buildDir;
        this.projectDir = projectDir;
    }

    /**
     * Start a process in detached mode using ProcessBuilder.
     * The process will continue running after Gradle exits.
     *
     * @param command The command to execute
     * @param identifier Identifier for log/pid files (e.g., module name)
     * @return Mock ExecResult indicating successful process start
     */
    protected ExecResult startDetachedProcess(final List<String> command, final String identifier) {
        try {
            final var pb = new ProcessBuilder(command);
            pb.directory(projectDir);

            // Redirect output to log files
            final File logFile = new File(buildDir, identifier + ".log");
            final File pidFile = new File(buildDir, identifier + ".pid");

            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
            pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile));

            final Process process = pb.start();
            final long pid = process.pid();

            // Write PID to file
            java.nio.file.Files.writeString(pidFile.toPath(), String.valueOf(pid));

            logger.lifecycle("[plugin] Started {} with PID: {}", identifier, pid);
            logger.lifecycle("[plugin] Logs: {}", logFile.getAbsolutePath());
            logger.lifecycle("[plugin] Stop with: kill {}", pid);
            logger.lifecycle("[plugin] PID saved to: {}", pidFile.getAbsolutePath());

            // Return mock ExecResult indicating successful start
            return MOCK_SUCCESS_RESULT;

        } catch (IOException e) {
            throw new GradleException("[plugin] Failed to start detached process for " + identifier, e);
        }
    }
}
