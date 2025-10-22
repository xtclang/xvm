package org.xtclang.plugin.launchers;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.gradle.api.logging.Logger;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecException;

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
        public ExecResult assertNormalExitValue() throws ExecException {
            return this;
        }

        @Override
        public ExecResult rethrowFailure() throws ExecException {
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
     * @param identifier Identifier for log files (e.g., module name)
     * @return Mock ExecResult indicating successful process start
     * @throws IOException if the process cannot be started
     */
    protected ExecResult startDetachedProcess(final List<String> command, final String identifier) throws IOException {
        final var pb = new ProcessBuilder(command);
        pb.directory(projectDir);

        // Create timestamp for log file name
        final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        final String logFileName = identifier.toLowerCase() + "_pid_" + timestamp + ".log";
        final File logFile = new File(buildDir, logFileName);

        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
        pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile));

        final Process process = pb.start();
        final long pid = process.pid();

        logger.lifecycle("""
            [plugin] Started {} with PID: {}
            [plugin] Stop with: kill {} (unless there is a graceful way to exit)
            [plugin] Logs: {}
            """.trim(), identifier, pid, pid, logFile.getAbsolutePath());

        // Return mock ExecResult indicating successful start
        return MOCK_SUCCESS_RESULT;
    }
}
