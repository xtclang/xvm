package org.xtclang.plugin.launchers;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static java.nio.file.Files.isDirectory;
import static org.xtclang.plugin.internal.DefaultXtcRunModule.DEFAULT_METHOD_NAME;

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
        final Path projectDir  = task.getProjectDirectory().get().getAsFile().toPath();
        final Path outputDir   = task.getOutputDirectoryInternal().getAsFile().toPath();
        final Path resourceDir = task.getResourceDirectoryInternal().getAsFile().toPath();
        // TODO: Revisit why we need to do relativize - it has something to do with build caching, I think.
        // TODO: Remove the String version of setOutputLocation, since we want the paths validated in one place only.
        // Add resource directory if it exists - if unspecified it is in the source set resource dir.
        // TODO: Check if we can skip the isDirectory check
        final var builder = CompilerOptions.builder()
            .forceRebuild(task.getRebuild().get())
            .enableShowVersion(task.getShowVersion().get())
            .enableVerbose(task.getVerbose().get())
            .disableWarnings(task.getDisableWarnings().get())
            .enableStrictMode(task.getStrict().get())
            .qualifyOutputNames(task.getQualifiedOutputName().get())
            .setOutputLocation(projectDir.relativize(outputDir));

        if (isDirectory(resourceDir)) {
            builder.addResourceLocation(projectDir.relativize(resourceDir));
        }
        // Add module paths
        for (final var modulePath : task.resolveFullModulePath()) {
            builder.addModulePath(projectDir.relativize(modulePath.toPath()).toString());
        }

        // Set module version if specified
        final String moduleVersion = task.resolveXtcVersion();
        if (moduleVersion != null) {
            builder.setModuleVersion(XtcCompileTask.semanticVersion(moduleVersion));
        }

        // Set qualified output names

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
    public static RunnerOptions buildRunnerOptions(final XtcRunTask task, final String moduleName, final List<String> moduleArgs) {
        final var projectDir = task.getProjectDirectory().get().getAsFile().toPath();
        final var methodName = task.getMethodName().getOrElse(DEFAULT_METHOD_NAME);
        assert !methodName.isEmpty();

        // TODO: Add build check for duplicate options, and make it possible to reset a set flag by
        //   removing a boolean flag (and argument) from the arg list, for robustness.
        // Set flags
        final var builder = RunnerOptions.builder()
            .enableShowVersion(task.getShowVersion().get())
            .enableVerbose(task.getVerbose().get())
            .setMethodName(methodName)
            .setTarget(moduleName, moduleArgs)
            .noRecompile(); // --no-recompile, note there is confusion with --rebuild in the compiler and this
        // Add module paths
        for (final var modulePath : task.resolveFullModulePath()) {
            builder.addModulePath(projectDir.relativize(modulePath.toPath()).toString());
        }
        return builder.build();
    }
}
