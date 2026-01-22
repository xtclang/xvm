package org.xtclang.plugin.launchers;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static java.nio.file.Files.isDirectory;
import static org.xtclang.plugin.internal.DefaultXtcRunModule.DEFAULT_METHOD_NAME;

import org.xtclang.plugin.tasks.XtcTestTask;
import org.xvm.tool.LauncherOptions.CompilerOptions;
import org.xvm.tool.LauncherOptions.RunnerOptions;
import org.xvm.tool.LauncherOptions.TestRunnerOptions;

import org.xtclang.plugin.tasks.XtcCompileTask;
import org.xtclang.plugin.tasks.XtcRunTask;

/**
 * Utility class to build CompilerOptions and RunnerOptions from tasks.
 * Centralizes ALL options-building logic so it's not duplicated across strategies.
 */
public final class LauncherOptionsBuilder {

    private final ExecutionMode mode;

    LauncherOptionsBuilder(final ExecutionMode mode) {
        this.mode = mode;
    }

    private boolean useAbsolutePaths() {
        return mode == ExecutionMode.DIRECT;
    }

    /**
     * Builds CompilerOptions from XtcCompileTask.
     * Used by ALL compile strategies (Direct, Attached, Detached).
     *
     * @param task the compile task
     */
    public CompilerOptions buildCompilerOptions(final XtcCompileTask task) {
        final Path projectDir  = task.getProjectDirectory().get().getAsFile().toPath();
        final Path outputDir   = task.getOutputDirectoryInternal().getAsFile().toPath();
        final Path resourceDir = task.getResourceDirectoryInternal().getAsFile().toPath();

        final var builder = CompilerOptions.builder()
            .forceRebuild(task.getRebuild().get())
            .enableShowVersion(task.getShowVersion().get())
            .enableVerbose(task.getVerbose().get())
            .disableWarnings(task.getDisableWarnings().get())
            .enableStrictMode(task.getStrict().get())
            .qualifyOutputNames(task.getQualifiedOutputName().get())
            .setOutputLocation(useAbsolutePaths() ? outputDir.toString() : projectDir.relativize(outputDir).toString());

        if (isDirectory(resourceDir)) {
            builder.addResourceLocation(useAbsolutePaths() ? resourceDir.toString() : projectDir.relativize(resourceDir).toString());
        }
        // Add module paths
        for (final var modulePath : task.resolveFullModulePath()) {
            builder.addModulePath(useAbsolutePaths() ? modulePath.getAbsolutePath() : projectDir.relativize(modulePath.toPath()).toString());
        }

        // Set module version if specified
        final String moduleVersion = task.resolveXtcVersion();
        if (moduleVersion != null) {
            builder.setModuleVersion(XtcCompileTask.semanticVersion(moduleVersion));
        }

        // Add source files
        for (final File sourceFile : task.resolveXtcSourceFiles()) {
            builder.addInputFile(useAbsolutePaths() ? sourceFile.getAbsolutePath() : projectDir.relativize(sourceFile.toPath()).toString());
        }

        return builder.build();
    }

    /**
     * Builds RunnerOptions from XtcRunTask.
     * Used by ALL run strategies (Direct, Attached, Detached).
     *
     * @param task the run task
     * @param moduleName the module to run
     * @param moduleArgs arguments to pass to the module
     */
    public RunnerOptions buildRunnerOptions(final XtcRunTask task, final String moduleName, final List<String> moduleArgs) {
        final var projectDir = task.getProjectDirectory().get().getAsFile().toPath();
        final var methodName = task.getMethodName().getOrElse(DEFAULT_METHOD_NAME);
        assert !methodName.isEmpty();

        // Set flags
        final var builder = RunnerOptions.builder()
            .enableShowVersion(task.getShowVersion().get())
            .enableVerbose(task.getVerbose().get())
            .setMethodName(methodName)
            .setTarget(moduleName, moduleArgs)
            .noRecompile(); // --no-recompile, note there is confusion with --rebuild in the compiler and this

        // Add module paths
        for (final var modulePath : task.resolveFullModulePath()) {
            builder.addModulePath(useAbsolutePaths() ? modulePath.getAbsolutePath() : projectDir.relativize(modulePath.toPath()).toString());
        }
        return builder.build();
    }

    /**
     * Builds TestRunnerOptions from XtcRunTask (for test tasks).
     * Used by ALL test strategies (Direct, Attached, Detached).
     *
     * @param task the run task (should be XtcTestTask)
     * @param moduleName the module to test
     * @param moduleArgs arguments to pass to the module
     */
    public TestRunnerOptions buildTestRunnerOptions(final XtcTestTask task, final String moduleName,
                                                    final List<String> moduleArgs) {
        final var projectDir = task.getProjectDirectory().get().getAsFile().toPath();
        final var outputDir  = task.getOutputDirectory();
        final var methodName = task.getMethodName().getOrElse(DEFAULT_METHOD_NAME);
        assert !methodName.isEmpty();

        // Set flags - same as runner options
        // Use explicit type to ensure we get TestRunnerOptions.Builder
        final TestRunnerOptions.Builder builder = TestRunnerOptions.builder()
                .setXUnitOutputDirectory(outputDir.get().getAsFile().getAbsolutePath());
        builder.enableShowVersion(task.getShowVersion().get())
            .enableVerbose(task.getVerbose().get())
            .setTarget(moduleName, moduleArgs)
            .noRecompile();

        // Add module paths
        for (final var modulePath : task.resolveFullModulePath()) {
            builder.addModulePath(useAbsolutePaths() ? modulePath.getAbsolutePath() : projectDir.relativize(modulePath.toPath()).toString());
        }
        return builder.build();
    }
}

