package org.xtclang.plugin.tasks;

import org.gradle.api.file.Directory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.xtclang.plugin.XtcCompilerExtension;
import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.launchers.CommandLine;
import org.xtclang.plugin.launchers.XtcLauncher;

import javax.inject.Inject;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static org.xtclang.plugin.XtcPluginConstants.XTC_COMPILER_CLASS_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_SOURCE_FILE_EXTENSION;
import static org.xtclang.plugin.XtcProjectDelegate.hasFileExtension;

@CacheableTask
public abstract class XtcCompileTask extends XtcSourceTask {
    private final XtcCompilerExtension ext;
    private final Provider<XtcLauncher> launcher;

    /**
     * Create an XTC Compile task. This goes through the Gradle build script, and task creation through
     * the project ObjectFactory.
     * <p<
     * The reason we need the @Inject is that the Kotlin compiler adds a secret parameter to the generated
     * constructor, if the class uses variables from the build script. That parameter is a reference to the
     * enclosing build script. As a result, the task needs to have the @Inject annotation in the constructor
     * so that Gradle will correctly instantiate the task with build script reference (which is what happens
     * when you instantiate it from the ObjectFactory in a project):
     */
    @Inject
    public XtcCompileTask(final XtcProjectDelegate project, final SourceSet sourceSet) {
        super(project, sourceSet);
        this.ext = project.xtcCompileExtension();
        this.launcher = project.getProject().provider(() -> XtcLauncher.create(project.getProject(), XTC_COMPILER_CLASS_NAME, ext));
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.ABSOLUTE)
    Provider<Directory> getInputXdkContents() {
        return project.getXdkContentsDir();
    }

    @OutputDirectory
    Provider<Directory> getOutputXtcModules() {
        return project.getXtcCompilerOutputDirModules(sourceSet);
    }

    @Input
    Property<Boolean> getFork() {
        return ext.getFork();
    }

    @Input
    Property<Boolean> getUseNativeLauncher() {
        return ext.getUseNativeLauncher();
    }

    @Input
    Property<Boolean> getLogOutputs() {
        return ext.getLogOutputs();
    }

    @Input
    ListProperty<String> getJvmArgs() {
        return ext.getJvmArgs();
    }

    @Input
    Property<Boolean> getNoWarn() {
        return ext.getNoWarn();
    }

    @Input
    Property<Boolean> getIsFork() {
        return ext.getFork();
    }

    @Input
    Property<Boolean> getIsVerbose() {
        return ext.getVerbose();
    }

    @Input
    Property<Boolean> getIsStrict() {
        return ext.getStrict();
    }

    @Input
    Property<Boolean> getQualifiedOutputName() {
        return ext.getQualifiedOutputName();
    }

    @Input
    Property<Boolean> getVersionedOutputName() {
        return ext.getVersionedOutputName();
    }

    @Input
    Property<Boolean> getForceRebuild() {
        return ext.getForceRebuild();
    }

    @Input
    MapProperty<Object, Object> getModuleFilenames() {
        return ext.getModuleFilenames();
    }

    @OutputDirectory
    Provider<Directory> getOutputDirectory() {
        return project.getXtcCompilerOutputDirModules(sourceSet);
    }

    @TaskAction
    public void compile() {
        start();

        final var args = new CommandLine(XTC_COMPILER_CLASS_NAME, getJvmArgs().get());

        if (getForceRebuild().get()) {
            project.warn("{} WARNING: Force Rebuild was set; touching everything in sourceSet '{}' and its resources.", prefix, sourceSet.getName());
            touchAllSource(); // The source set remains the same, but hopefully doing this "touch" as the first executable action will still be before computation of changes.
        }

        File outputDir = project.getXtcCompilerOutputDirModules(sourceSet).get().getAsFile();
        args.add("-o", outputDir.getAbsolutePath());

        project.info("{} Output directory for {} is : {}", prefix, sourceSet.getName(), outputDir);

        sourceSet.getResources().getSrcDirs().forEach(dir -> {
            project.info("{} Resolving resource dir (build): '{}'.", prefix, dir);
            if (!dir.exists()) {
                project.info("{} Resource does not exist: '{}' (ignoring passing as input to compiler)", prefix, dir);
            } else {
                project.info("{} Adding resource: {}", prefix, dir);
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

        handleExecResult(project.getProject(), launcher.get().apply(args));
        // TODO: A little bit kludgy, but the outputFilename property in the xtcCompile extension as some directory vs file issue (a bug).
        renameOutputs();
    }

    private void renameOutputs() {
        project.getProject().fileTree(getOutputXtcModules()).filter(project::isXtcBinary).forEach(file -> {
            final String oldName = file.getName();
            final String newName = ext.resolveModuleFilename(oldName);
            if (oldName.equals(newName)) {
                project.info("{} Finalizing compiler output XTC binary filename: '{}'", prefix, oldName);
            } else {
                project.info("{} Changing and finalizing compiler output XTC filename: '{}' to '{}'", prefix, oldName, newName);
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

    private boolean isTopLevelSource(final File file) {
        assert file.isFile();
        final var topLevelSourceDirs = new HashSet<>(sourceSet.getAllSource().getSrcDirs());
        final var dir = file.getParentFile();
        assert (dir != null && dir.isDirectory());
        final var isTopLevelSrc = topLevelSourceDirs.contains(dir);
        project.info("{} Checking if {} is a module definition (currently, just checking if it's a top level file): {}", prefix, file.getAbsolutePath(), isTopLevelSrc);
        if (isTopLevelSrc || "mack.x".equalsIgnoreCase(file.getName())) {
            project.info("{} Found module definition: {}", prefix, file.getAbsolutePath());
            return true;
        }
        return false;
    }
}
