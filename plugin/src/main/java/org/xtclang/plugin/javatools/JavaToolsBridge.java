package org.xtclang.plugin.javatools;

import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.tool.Compiler;
import org.xvm.tool.Launcher;
import org.xvm.tool.Launcher.LauncherException;
import org.xvm.tool.LauncherOptions.CompilerOptions;

import org.xtclang.plugin.tasks.XtcCompileTask;

import java.io.File;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

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
        private static final long serialVersionUID =-1L;

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
     * @param launcherType The type of launcher ("xcc" or "xec")
     * @param args The command-line arguments
     * @return The execution result
     */
    @SuppressWarnings("unused")
    public BridgeResult execute(final String launcherType, final String[] args) {
        try {
            // Create ErrorListener to capture errors
            final ErrorListener errListener = new ErrorList();

            // Map the launcher type to the command name
            final String cmd = mapLauncherType(launcherType);
            final int exitCode = Launcher.launch(cmd, args, errListener);

            if (exitCode != 0) {
                return BridgeResult.error(exitCode, "Execution failed with exit code: " + exitCode);
            }
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

    private String mapLauncherType(final String launcherType) {
        return switch (launcherType.toLowerCase()) {
            case "compiler", "xcc" -> "xcc";
            case "runner", "xec" -> "xec";
            default -> throw new IllegalArgumentException("Unknown launcher type: " + launcherType);
        };
    }

    /**
     * Compiles XTC source using the Compiler API directly instead of command-line args.
     * The bridge reads configuration from the task and builds CompilerOptions programmatically.
     *
     * @param task the compile task with configuration
     * @return the execution result
     */
    @SuppressWarnings("unused")
    public BridgeResult compile(final XtcCompileTask task) {
        try {
            // Create ErrorListener to capture errors
            final ErrorListener errListener = new ErrorList();

            // Build CompilerOptions from task configuration
            final CompilerOptions options = buildCompilerOptions(task);

            // Create and run compiler with options
            final Compiler compiler = new Compiler(options, null, errListener);
            compiler.run();

            return BridgeResult.success();
        } catch (final LauncherException e) {
            final int exitCode = e.error ? 1 : 0;
            final String errorMessage = buildErrorMessage(e);
            return BridgeResult.error(exitCode, errorMessage);
        } catch (final Exception e) {
            return BridgeResult.error(1, "Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Builds CompilerOptions from XtcCompileTask configuration.
     * Directly calls task methods - no reflection needed since the task class is visible.
     */
    private CompilerOptions buildCompilerOptions(final XtcCompileTask task) {
        final CompilerOptions.Builder builder = new CompilerOptions.Builder();

        // Output directory
        final File outputDir = task.getOutputDirectoryInternal().getAsFile();
        builder.output(outputDir);

        // Resource directory
        final File resourceDir = task.getResourceDirectoryInternal().getAsFile();
        if (resourceDir != null && resourceDir.exists()) {
            builder.resource(resourceDir);
        }

        // Boolean flags
        builder.rebuild(task.getRebuild().get());
        builder.nowarn(task.getDisableWarnings().get());
        builder.strict(task.getStrict().get());
        builder.qualify(task.getQualifiedOutputName().get());

        // Module version
        final String moduleVersion = task.resolveModuleVersion();
        if (moduleVersion != null) {
            builder.version(moduleVersion);
        }

        // Module path - Note: resolveFullModulePath() is protected, task should expose it
        final List<File> modulePath = task.getModulePath().getFiles().stream().collect(java.util.stream.Collectors.toList());
        for (final File path : modulePath) {
            builder.modulePath(path);
        }

        // Source files
        final Set<File> sourceFiles = task.resolveXtcSourceFiles();
        for (final File sourceFile : sourceFiles) {
            builder.input(sourceFile);
        }

        return builder.build();
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
