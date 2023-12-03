package org.xtclang.plugin.tasks;

import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.xtclang.plugin.launchers.CommandLine;
import org.xtclang.plugin.XtcCompilerExtension;
import org.xtclang.plugin.launchers.XtcLauncher;
import org.xtclang.plugin.XtcProjectDelegate;

import javax.inject.Inject;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_GROUP;
import static org.xtclang.plugin.Constants.XTC_COMPILER_CLASS_NAME;
import static org.xtclang.plugin.Constants.XTC_CONFIG_NAME_JAVATOOLS_INCOMING;
import static org.xtclang.plugin.Constants.XTC_LANGUAGE_NAME;
import static org.xtclang.plugin.Constants.XTC_SOURCE_FILE_EXTENSION;
import static org.xtclang.plugin.tasks.XtcExtractXdkTask.EXTRACT_TASK_NAME;
import static org.xtclang.plugin.XtcProjectDelegate.hasFileExtension;
import static org.xtclang.plugin.XtcProjectDelegate.incomingXtcModuleDependencies;

@CacheableTask
public abstract class XtcCompileTask extends XtcSourceTask {
    private final XtcProjectDelegate project;
    private final String prefix;
    private final SourceSet sourceSet;
    private final XtcCompilerExtension extCompiler;
    private final XtcLauncher launcher;

    @Inject
    public XtcCompileTask(final XtcProjectDelegate project, final SourceSet sourceSet) {
        super(project);
        this.project = project;
        this.prefix = project.prefix();
        this.sourceSet = sourceSet;
        this.extCompiler = project.xtcCompileExtension();
        this.launcher = XtcLauncher.create(project.getProject(), XTC_COMPILER_CLASS_NAME, getIsFork().get(), getUseNativeLauncher().get());
        configureTask();
    }

    private void configureTask() {
        final var name = getName();
        setGroup(BUILD_GROUP);
        setDescription("Compile an XTC source set, similar to the JavaCompile task for Java.");

        dependsOn(EXTRACT_TASK_NAME);
        setSource(sourceSet.getExtensions().getByName(XTC_LANGUAGE_NAME));

        project.info("{} Associating {} compile task {} with SourceDirectorySet: {}", prefix, sourceSet.getName(), name, getSource().getFiles());

        if (extCompiler.getForceRebuild().get()) {
            project.warn("{} WARNING: Force Rebuild was set; touching everything in sourceSet '{}' and its resources.", prefix, sourceSet.getName());
            project.alwaysRerunTask(this);
            touchAllSource();
        }

        doLast(t -> {
            // This happens during task execution, after the config phase.
            project.info("{} '{}' Finished. Outputs in: {}", prefix, t.getName(), t.getOutputs().getFiles().getAsFileTree());
            sourceSet.getOutput().getAsFileTree().forEach(it -> project.info("{} compileXtc sourceSet output: {}", prefix, it));
        });

        project.info("{} '{}' Registered and configured compile task for sourceSet: {}", prefix, getName(), sourceSet.getName());
    }

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public FileCollection getInputXtcJavaToolsConfig() {
        return project.getProject().files(project.getProject().getConfigurations().getByName(XTC_CONFIG_NAME_JAVATOOLS_INCOMING));
    }

    // Depend on xdkModule declarations if declared (or xtcModuleTest for test source set)
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public FileCollection getInputDeclaredDependencyModules() {
        // The source sets are already an implicit input since this is a source task.
        return project.filesFrom(incomingXtcModuleDependencies(sourceSet)); // look for xdkModules that have been added as dependencies.
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public Provider<Directory> getInputXdkContents() {
        return project.getXdkContentsDir();
    }

    @OutputDirectory
    public Provider<Directory> getOutputXtcModules() {
        return project.getXtcCompilerOutputDirModules(sourceSet);
    }

    @Input
    public Property<Boolean> getFork() {
        return extCompiler.getFork();
    }

    @Input
    Property<Boolean> getUseNativeLauncher() {
        return extCompiler.getUseNativeLauncher();
    }

    @Input
    public ListProperty<String> getJvmArgs() {
        return extCompiler.getJvmArgs();
    }

    @Input
    public Property<Boolean> getNoWarn() {
        return extCompiler.getNoWarn();
    }

    @Input
    public Property<Boolean> getIsFork() {
        return extCompiler.getFork();
    }

    @Input
    public Property<Boolean> getIsVerbose() {
        return extCompiler.getVerbose();
    }

    @Input
    public Property<Boolean> getIsStrict() {
        return extCompiler.getStrict();
    }

    @Input
    public Property<Boolean> getQualifiedOutputName() {
        return extCompiler.getQualifiedOutputName();
    }

    @Input
    public Property<Boolean> getVersionedOutputName() {
        return extCompiler.getVersionedOutputName();
    }

    @Input
    public Property<Boolean> getForceRebuild() {
        return extCompiler.getForceRebuild();
    }

    @Input
    public MapProperty<String, String> getRenameOutput() {
        return extCompiler.getRenameOutput();
    }

    @Input
    public Property<String> getOutputFilename() {
        return extCompiler.getOutputFilename();
    }

    @OutputDirectory
    public Provider<Directory> getOutputDirectory() {
        return project.getXtcCompilerOutputDirModules(sourceSet);
    }

    @TaskAction
    public void compile() {
        final var args = new CommandLine(XTC_COMPILER_CLASS_NAME, getJvmArgs().get());

        final var outputFilename = getOutputFilename().get();
        if (outputFilename.contains(File.separator)) {
            throw project.buildException(String.format("%s has invalid output filename: %s.", prefix, outputFilename));
        }

        File outputDir = project.getXtcCompilerOutputDirModules(sourceSet).get().getAsFile();
        if (!outputFilename.isEmpty()) {
            outputDir = new File(outputDir, outputFilename);
            project.warn("{} Overrides outputDir with output filename path: {}", prefix, outputFilename);
        }
        args.add("-o", outputDir.getAbsolutePath());
        project.info("{} Output directory for {} is : {}", prefix, sourceSet.getName(), outputDir);

        // Resource should already be an input because we inherit the SourceTask, which uses a project wide file change policy.
        final var resourceDirs = sourceSet.getResources().getSrcDirs();
        resourceDirs.forEach(dir -> {
            if (!dir.exists()) {
                project.info("{} Resource directory does not exist: {} (ignoring passing as input to compiler)", prefix, dir);
            } else {
                project.info("{} Adding resource directory: {}", prefix, dir);
                args.add("-r", dir.getAbsolutePath());
            }
        });

        args.addBoolean("-nowarn", getNoWarn().get());
        args.addBoolean("-verbose", getIsVerbose().get());
        args.addBoolean("-strict", getIsStrict().get());
        args.addBoolean("-qualify", getQualifiedOutputName().get());
        args.addBoolean("-rebuild", getForceRebuild().get());
        args.addBoolean("-version", getVersionedOutputName().get());

        args.addRepeated("-L", resolveXtcModulePath());
        final var sourceFiles = resolveXtcSourceFiles().stream().map(File::getAbsolutePath).toList();
        if (sourceFiles.isEmpty()) {
            project.warn("{} No source file found for sourceSet: '{}'", prefix, sourceSet.getName());
        }
        sourceFiles.forEach(args::addRaw);

        handleExecResult(project.getProject(), launcher.apply(args));
        // TODO: A little bit kludgy, but the outputFilename property in the xtcCompile extension as some directory vs file issue (a bug).
        renameOutputs();
    }

    private void renameOutputs() {
        project.getProject().fileTree(getOutputXtcModules()).filter(project::isXtcBinary).forEach(file -> {
            final String oldName = file.getName();
            final String newName = getRenameOutput().get().get(oldName);
            project.info("{} Filtering file {} to {}", prefix, oldName, newName);
            if (newName != null) {
                final File newFile = new File(file.getParentFile(), newName);
                project.info("{} File tree scan: {} should be renamed to {}", project, file, newFile);
                if (!file.renameTo(newFile)) {
                    // TODO does this update the output? Seems like it. Write a unit test.
                    throw project.buildException("Failed to rename " + file + " to " + newFile);
                }
            }
        });
    }

    private Set<File> resolveXtcSourceFiles() {
        final var resolvedSources = getSource().filter(this::isTopLevelXtcSourceFile).getFiles();
        project.info("{} Resolved top level sources (should be module definitions, or XTC will fail later): {}", prefix, resolvedSources);
        return resolvedSources;
    }

    private Set<File> resolveXtcModulePath() {
        return project.resolveModulePath(getName(), getInputDeclaredDependencyModules());
    }

    private boolean isTopLevelSource(final File file) {
        assert file.isFile();
        final var topLevelSourceDirs = new HashSet<>(sourceSet.getAllSource().getSrcDirs());
        final var dir = file.getParentFile();
        assert(dir != null && dir.isDirectory());
        final var isTopLevelSrc = topLevelSourceDirs.contains(dir);
        project.info("{} Checking if {} is a module definition (currently, just checking if it's a top level file): {}", prefix, file.getAbsolutePath(), isTopLevelSrc);
        if (isTopLevelSrc || "mack.x".equalsIgnoreCase(file.getName())) {
            project.info("{} Found module definition: {}", prefix, file.getAbsolutePath());
            return true;
        }
        return false;
    }

    private boolean isXtcSourceFile(final File file) {
        // TODO: Previously we called a Launcher method to ensure this was a module, but all these files should be in the top
        //   level directory of a source set, and this means that xtc will assume they are all module definitions, and fail if this
        //   is not the case. We used to check for this in the plugin, but we really do not want the compile time dependency to
        //   the javatools.jar in the plugin, as the plugin comes in early. This would have bad side effects, like "clean" would
        //   need to build the javatools.jar, if it wasn't there, just to immediately delete it again.
        return file.isFile() && hasFileExtension(file, XTC_SOURCE_FILE_EXTENSION);
    }

    private boolean isTopLevelXtcSourceFile(final File file) {
        return !file.isDirectory() && isXtcSourceFile(file) && isTopLevelSource(file);
    }
}
