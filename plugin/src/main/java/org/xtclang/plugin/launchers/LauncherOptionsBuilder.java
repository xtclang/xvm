package org.xtclang.plugin.launchers;

import java.io.File;

import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.LauncherOptions.RunnerOptions;

import org.xtclang.plugin.tasks.XtcCompileTask;
import org.xtclang.plugin.tasks.XtcRunTask;

/**
 * Utility class to build CompilerOptions and RunnerOptions from tasks.
 * Centralizes ALL options-building logic so it's not duplicated across strategies.
 */
public final class LauncherOptionsBuilder {

    private LauncherOptionsBuilder() {
        // Utility class
    }

    /**
     * Builds CompilerOptions from XtcCompileTask.
     * Used by ALL compile strategies (Direct, Attached, Detached).
     */
    public static CompilerOptions buildCompilerOptions(final XtcCompileTask task) {
        final var projectDir = task.getProjectDirectory().get().getAsFile().toPath();
        final var outputDir = task.getOutputDirectoryInternal();
        final var builder = CompilerOptions.builder();

        // Set output location
        builder.setOutputLocation(projectDir.relativize(outputDir.getAsFile().toPath()).toString());

        // Set flags
        builder.forceRebuild(task.getRebuild().get());
        builder.enableShowVersion(task.getShowVersion().get());
        builder.enableVerbose(task.getVerbose().get());
        builder.disableWarnings(task.getDisableWarnings().get());
        builder.enableStrictMode(task.getStrict().get());

        // Add module paths
        for (final var modulePath : task.resolveFullModulePath()) {
            builder.addModulePath(projectDir.relativize(modulePath.toPath()).toString());
        }

        // Set module version if specified
        final String moduleVersion = task.resolveModuleVersion();
        if (moduleVersion != null) {
            builder.setModuleVersion(XtcCompileTask.semanticVersion(moduleVersion));
        }

        // Set qualified output names
        builder.qualifyOutputNames(task.getQualifiedOutputName().get());

        // Add source files
        for (final File sourceFile : task.resolveXtcSourceFiles()) {
            builder.addInputFile(projectDir.relativize(sourceFile.toPath()).toString());
        }

        return builder.build();
    }

    /**
     * Builds RunnerOptions from XtcRunTask.
     * Used by ALL run strategies (Direct, Attached, Detached).
     */
    public static RunnerOptions buildRunnerOptions(final XtcRunTask task, final String moduleName, final String[] moduleArgs) {
        final var projectDir = task.getProjectDirectory().get().getAsFile().toPath();
        final var builder = RunnerOptions.builder();

        // Set flags
        builder.enableShowVersion(task.getShowVersion().get());
        builder.enableVerbose(task.getVerbose().get());
        builder.disableRebuild(true); // --no-recompile

        // Add module paths
        for (final var modulePath : task.resolveFullModulePath()) {
            builder.addModulePath(projectDir.relativize(modulePath.toPath()).toString());
        }

        // Set method name if not default
        final String methodName = task.getMethodName().getOrNull();
        if (methodName != null && !methodName.isEmpty()) {
            builder.setMethodName(methodName);
        }

        // Set target module and args
        builder.setTarget(moduleName, moduleArgs);

        return builder.build();
    }
}