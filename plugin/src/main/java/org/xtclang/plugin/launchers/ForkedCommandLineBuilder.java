package org.xtclang.plugin.launchers;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static java.nio.file.Files.isDirectory;
import static org.xtclang.plugin.internal.DefaultXtcRunModule.DEFAULT_METHOD_NAME;

import org.xtclang.plugin.tasks.XtcCompileTask;
import org.xtclang.plugin.tasks.XtcRunTask;
import org.xtclang.plugin.tasks.XtcTestTask;

/**
 * Builds launcher command-line arguments for forked execution without requiring
 * javatools types in the outer plugin classloader.
 */
public final class ForkedCommandLineBuilder {

    public String[] buildCompilerArgs(final XtcCompileTask task) {
        final Path projectDir = task.getProjectDirectory().get().getAsFile().toPath();
        final Path outputDir = task.getOutputDirectoryInternal().getAsFile().toPath();
        final Path resourceDir = task.getResourceDirectoryInternal().getAsFile().toPath();

        final List<String> args = new ArrayList<>();
        addBooleanFlag(args, task.getRebuild().get(), "--rebuild");
        addBooleanFlag(args, task.getShowVersion().get(), "--version");
        addBooleanFlag(args, task.getVerbose().get(), "-v");
        addBooleanFlag(args, task.getDisableWarnings().get(), "--nowarn");
        addBooleanFlag(args, task.getStrict().get(), "--strict");
        addBooleanFlag(args, task.getQualifiedOutputName().get(), "--qualify");
        args.addAll(List.of("-o", relativize(projectDir, outputDir)));

        if (isDirectory(resourceDir)) {
            args.addAll(List.of("-r", relativize(projectDir, resourceDir)));
        }

        for (final var modulePath : task.resolveFullModulePath()) {
            args.addAll(List.of("-L", relativize(projectDir, modulePath.toPath())));
        }

        final String moduleVersion = task.resolveXtcVersion();
        if (moduleVersion != null && !moduleVersion.isBlank()) {
            args.addAll(List.of("--set-version", XtcCompileTask.semanticVersion(moduleVersion)));
        }

        for (final File sourceFile : task.resolveXtcSourceFiles()) {
            args.add(relativize(projectDir, sourceFile.toPath()));
        }

        return args.toArray(String[]::new);
    }

    public String[] buildRunnerArgs(final XtcRunTask task, final String moduleName, final List<String> moduleArgs) {
        final List<String> args = new ArrayList<>();
        addBooleanFlag(args, task.getShowVersion().get(), "--version");
        addBooleanFlag(args, task.getVerbose().get(), "-v");

        final var methodName = task.getMethodName().getOrElse(DEFAULT_METHOD_NAME);
        if (!DEFAULT_METHOD_NAME.equals(methodName)) {
            args.addAll(List.of("-M", methodName));
        }

        for (final var modulePath : task.resolveFullModulePath()) {
            args.addAll(List.of("-L", modulePath.getAbsolutePath()));
        }

        args.add("--no-recompile");
        args.add(moduleName);
        args.addAll(moduleArgs);
        return args.toArray(String[]::new);
    }

    public String[] buildTestRunnerArgs(final XtcTestTask task, final String moduleName, final List<String> moduleArgs) {
        final List<String> args = new ArrayList<>();
        addBooleanFlag(args, task.getShowVersion().get(), "--version");
        addBooleanFlag(args, task.getVerbose().get(), "-v");

        for (final var modulePath : task.resolveFullModulePath()) {
            args.addAll(List.of("-L", modulePath.getAbsolutePath()));
        }

        args.addAll(List.of("--xunit-out", task.getOutputDirectory().get().getAsFile().getAbsolutePath()));
        args.add("--no-recompile");
        args.add(moduleName);
        args.addAll(moduleArgs);
        return args.toArray(String[]::new);
    }

    private static void addBooleanFlag(final List<String> args, final boolean enabled, final String flag) {
        if (enabled) {
            args.add(flag);
        }
    }

    private static String relativize(final Path projectDir, final Path target) {
        return projectDir.relativize(target).toString();
    }
}
