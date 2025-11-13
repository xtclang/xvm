package org.xtclang.plugin.javatools;

import org.xvm.tool.Compiler;
import org.xvm.tool.Launcher.LauncherException;
import org.xvm.tool.Runner;

import java.io.Serial;
import java.io.Serializable;
import java.util.function.Consumer;

/**
 * Bridge class that isolates all direct javatools type references.
 *
 * <p>This class is loaded via a custom URLClassLoader that includes javatools.jar,
 * allowing it to directly reference javatools types without requiring javatools
 * on the plugin's main classloader.
 *
 * <p>The bridge pattern allows:
 * <ul>
 *   <li>Type-safe access to javatools APIs (no reflection needed in bridge code)</li>
 *   <li>Proper exception handling (can catch LauncherException)</li>
 *   <li>Clean separation between plugin code and javatools-dependent code</li>
 *   <li>No need to publish javatools as a runtime dependency</li>
 * </ul>
 */
public final class JavaToolsBridge {

    /**
     * Result of executing a javatools command.
     * Serializable so it can be passed back across classloader boundaries.
     */
    public static final class BridgeResult implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        public final boolean success;
        public final int exitCode;
        public final String errorMessage;

        private BridgeResult(final boolean success, final int exitCode, final String errorMessage) {
            this.success = success;
            this.exitCode = exitCode;
            this.errorMessage = errorMessage;
        }

        public static BridgeResult success() {
            return new BridgeResult(true, 0, null);
        }

        public static BridgeResult error(final int exitCode, final String message) {
            return new BridgeResult(false, exitCode, message);
        }
    }

    /**
     * Executes a javatools launcher with the given arguments.
     *
     * <p>This method can directly catch LauncherException because it's loaded
     * in a classloader that has javatools.jar.
     *
     * @param launcherType The type of launcher ("compiler" or "runner")
     * @param args The command-line arguments
     * @return The execution result
     */
    @SuppressWarnings("unused")
    public BridgeResult execute(final String launcherType, final String[] args) {
        try {
            final Consumer<String[]> launcher = getLauncher(launcherType);
            launcher.accept(args);
            return BridgeResult.success();
        } catch (final LauncherException e) {
            // Can catch LauncherException here because we're in the javatools classloader!
            final int exitCode = e.error ? 1 : 0;
            final String errorMessage = buildErrorMessage(e);
            return BridgeResult.error(exitCode, errorMessage);

        } catch (final Exception e) {
            // Unexpected exception
            return BridgeResult.error(1, "Unexpected error: " + e.getMessage());
        }
    }

    private Consumer<String[]> getLauncher(final String launcherType) {
        return switch (launcherType.toLowerCase()) {
            case "compiler" -> Compiler::launch;
            case "runner" -> Runner::launch;
            default -> throw new IllegalArgumentException("Unknown launcher type: " + launcherType);
        };
    }

    /**
     * Extracts error message and cause chain from LauncherException.
     */
    private String buildErrorMessage(final LauncherException e) {
        final StringBuilder message = new StringBuilder();

        if (e.getMessage() != null && !e.getMessage().isEmpty()) {
            message.append(e.getMessage());
        }

        // Add cause chain
        Throwable cause = e.getCause();
        while (cause != null) {
            if (!message.isEmpty()) {
                message.append("\nCaused by: ");
            }
            message.append(cause.getClass().getSimpleName());
            if (cause.getMessage() != null) {
                message.append(": ").append(cause.getMessage());
            }
            cause = cause.getCause();
        }

        return message.toString();
    }
}
