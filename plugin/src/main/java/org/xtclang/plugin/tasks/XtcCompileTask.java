package org.xtclang.plugin.tasks;

import kotlin.Pair;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.xtclang.plugin.XtcCompilerExtension;
import org.xtclang.plugin.XtcPluginUtils.FileUtils;
import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.launchers.CommandLine;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.xtclang.plugin.XtcPluginConstants.XTC_COMPILER_CLASS_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_COMPILER_LAUNCHER_NAME;

@CacheableTask
public class XtcCompileTask extends XtcSourceTask implements XtcCompilerExtension {
    // Per-task configuration properties only.
    private final ListProperty<String> outputFilenames;

    // Default values for inputs and outputs below are inherited from extension, can be reset per task
    protected final Property<Boolean> disableWarnings;
    protected final Property<Boolean> isStrict;
    protected final Property<Boolean> hasQualifiedOutputName;
    protected final Property<Boolean> hasVersionedOutputName;
    protected final Property<String> xtcVersion;
    protected final Property<Boolean> shouldForceRebuild;

    /**
     * Create an XTC Compile task. This goes through the Gradle build script, and task creation through
     * the project ObjectFactory.
     * <p>
     * The reason we need the @Inject is that the Kotlin compiler adds a secret parameter to the generated
     * constructor, if the class uses variables from the build script. That parameter is a reference to the
     * enclosing build script. As a result, the task needs to have the @Inject annotation in the constructor
     * so that Gradle will correctly instantiate the task with build script reference (which is what happens
     * when you instantiate it from the ObjectFactory in a project):
     */
    @Inject
    public XtcCompileTask(final Project project) {
        // the compile task name should be determined by its source set
        // a runtime task can by default depend on all source sets, it's fine. even a test task.
        super(project);

        // The outputFilenames property only exists in the compile task, not in the compile configuration.
        this.outputFilenames = objects.listProperty(String.class).value(new ArrayList<>());

        // Conventions inherited from extension; can be reset on a per-task basis, of course.
        this.disableWarnings = objects.property(Boolean.class).convention(ext.getDisableWarnings());
        this.isStrict = objects.property(Boolean.class).convention(ext.getStrict());
        this.hasQualifiedOutputName = objects.property(Boolean.class).convention(ext.getQualifiedOutputName());
        this.hasVersionedOutputName = objects.property(Boolean.class).convention(ext.getVersionedOutputName());
        this.shouldForceRebuild = objects.property(Boolean.class).convention(ext.getForceRebuild());
        this.xtcVersion = objects.property(String.class).convention(ext.getXtcVersion());
    }

    private String getCompileSourceSetName() {
        final String taskName = getName();
        if (taskName.equals("compileXtc")) {
            return SourceSet.MAIN_SOURCE_SET_NAME;
        }
        final int compileIndex = taskName.indexOf("compile");
        final int xtcIndex = taskName.indexOf("Xtc");
        if (compileIndex == -1 || xtcIndex == -1) {
            throw buildException("Failed to resolve source set name from compile task '{}'", taskName);
        }
        final String sourceSetName = taskName.substring(compileIndex + "compile".length(), xtcIndex);
        return sourceSetName.toLowerCase();
    }

    private SourceSet getCompileSourceSet() {
        return XtcProjectDelegate.resolveSourceSet(project, getCompileSourceSetName());
    }

    private boolean isMainSourceSetCompileTask() {
        return SourceSet.MAIN_SOURCE_SET_NAME.equals(getCompileSourceSetName());
    }

    // There is one source set to compile, but there other may be needed for the module path.
    // The compileXXXXtc task should only use XXX as sources, but the module path for compilation
    // should point out the other source sets as well, for example for a test task, but not for the
    // main task. I suppose the most generic way to do this is to be able to add a source set to the
    // module path in the task, but let's try to work incrementally.

    @Internal
    @Override
    public final String getNativeLauncherCommandName() {
        return XTC_COMPILER_LAUNCHER_NAME;
    }

    @Internal
    @Override
    public final String getJavaLauncherClassName() {
        return XTC_COMPILER_CLASS_NAME;
    }

    /**
     * Add an output Filename mapping.
     * TODO Why does IntelliJ think these are unused? Check that it doesn't lead to any unknown dependency problems for the Plugin.
     */
    public void outputFilename(final String from, final String to) {
        outputFilenames.add(from);
        outputFilenames.add(to);
    }

    public void outputFilename(final Pair<String, String> pair) {
        outputFilenames.add(pair.getFirst());
        outputFilenames.add(pair.getSecond());
    }

    public void outputFilename(final Provider<String> from, final Provider<String> to) {
        outputFilenames.add(from);
        outputFilenames.add(to);
    }

    @Input
    ListProperty<String> getOutputFilenames() {
        return outputFilenames;
    }

    @Input
    @Override
    public Property<Boolean> getQualifiedOutputName() {
        return hasQualifiedOutputName;
    }

    @Input
    @Override
    public Property<Boolean> getVersionedOutputName() {
        return hasVersionedOutputName;
    }

    @Optional
    @Input
    @Override
    public Property<String> getXtcVersion() {
        return xtcVersion;
    }

    @Input
    @Override
    public Property<Boolean> getStrict() {
        return isStrict;
    }

    @Input
    @Override
    public Property<Boolean> getForceRebuild() {
        return shouldForceRebuild;
    }

    @Input
    @Override
    public Property<Boolean> getDisableWarnings() {
        return disableWarnings;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    FileCollection getResourceDirectory() {
        // TODO: This is wrong. The compile task should not be the one depending on resources src, but resources build.
        //   But that is java behavior, so make sure at least we get the resource input dependency.
        return getCompileSourceSet().getResources().getSourceDirectories();
    }

    @OutputDirectory
    Provider<Directory> getOutputDirectory() {
        // TODO We can make this configurable later.
        return XtcProjectDelegate.getXtcSourceSetOutputDirectory(project, getCompileSourceSet());
    }

    @TaskAction
    @Override
    public void executeTask() {
        super.executeTask();

        final var prefix = prefix();
        final var sourceSet = getCompileSourceSet();
        final var args = new CommandLine(XTC_COMPILER_CLASS_NAME, getJvmArgs().get());

        if (getForceRebuild().get()) {
            logger.warn("{} WARNING: Force Rebuild was set; touching everything in sourceSet '{}' and its resources.", prefix, sourceSet.getName());
            touchAllSource(); // The source set remains the same, but hopefully doing this "touch" as the first executable action will still be before computation of changes.
        }

        final File outputDir = getOutputDirectory().get().getAsFile();
        args.add("-o", outputDir.getAbsolutePath());

        logger.info("{} Output directory for {} is : {}", prefix, sourceSet.getName(), outputDir);

        sourceSet.getResources().getSrcDirs().forEach(dir -> {
            logger.info("{} Resolving resource dir (build): '{}'.", prefix, dir);
            if (!dir.exists()) {
                logger.info("{} Resource does not exist: '{}' (ignoring passing as input to compiler)", prefix, dir);
            } else {
                logger.info("{} Adding resource: {}", prefix, dir);
                args.add("-r", dir.getAbsolutePath());
            }
        });

        args.addBoolean("--version", getShowVersion().get());
        args.addBoolean("--rebuild", getForceRebuild().get());
        args.addBoolean("--nowarn", getDisableWarnings().get());
        args.addBoolean("--verbose", getIsVerbose().get());
        args.addBoolean("--strict", getStrict().get());
        args.addBoolean("--qualify", getQualifiedOutputName().get());
        // If xtcVersion is set, we stamp that, otherwise we ignore it for now. It may be that we should stamp it as the xcc version used to compile if no flag is given?
        final String moduleVersion = resolveModuleVersion();
        if (moduleVersion != null) {
            if (hasVerboseLogging()) {
                logger.lifecycle("{} Stamping XTC module with version: '{}'", prefix, moduleVersion);
            }
            args.add("--set-version", moduleVersion);
        }
        args.addRepeated("-L", resolveFullModulePath());
        final var sourceFiles = resolveXtcSourceFiles().stream().map(File::getAbsolutePath).sorted().toList();
        if (sourceFiles.isEmpty()) {
            logger.warn("{} No source file found for source set: '{}'", prefix, sourceSet.getName());
        }
        sourceFiles.forEach(args::addRaw);

        final var launcher = createLauncher();
        handleExecResult(launcher.apply(args));
        finalizeOutputs();
        // TODO outputFilename default task property?
    }

    private String resolveModuleVersion() {
        // TODO: We need to tell the plugin, when we build it, which version it has from the catalog. This is actually the XTC artifact that needs to be asked its version. The launcher? the xdk dependency? Figure this one out.
        if (getXtcVersion().isPresent()) {
            return getXtcVersion().get();
        }
        return null;
    }

    private void finalizeOutputs() {
        // If any of the outputs aren't valid XTC modules, they are ignored. We should probably warn, or break the build instead?
        final var prefix = prefix();

        // Do not keep iterating through the connection, updated life. Finalize it first.
        final var xtcModuleFiles = project.fileTree(getOutputDirectory()).filter(FileUtils::isValidXtcModule).getFiles();
        for (final var oldFile : xtcModuleFiles) {
            final String oldName = oldFile.getName();
            final String newName = resolveOutputFilename(oldName);
            if (oldName.equals(newName)) {
                logger.info("{} Finalizing compiler output XTC binary filename: '{}'", prefix, oldName);
            } else {
                final File newFile = new File(oldFile.getParentFile(), newName);
                logger.info("{} Renaming and finalizing compiler output XTC filename: '{}' to '{}'", prefix, oldName, newName);
                logger.info("{} File tree scan: {} should be renamed to {}", prefix, oldFile, newFile);
                if (!oldFile.renameTo(newFile)) {
                    // TODO does this update the output? Seems like it. Write a unit test.
                    throw buildException("Failed to rename '{}' to '{}", oldFile, newFile);
                }
            }
        }
    }

    private Set<File> resolveXtcSourceFiles() {
        final var resolvedSources = getSource().filter(file -> isTopLevelXtcSourceFile(getCompileSourceSet(), file)).getFiles();
        logger.info("{} Resolved top level sources (should be module definitions, or XTC will fail later): {}", prefix(), resolvedSources);
        return resolvedSources;
    }

    private String resolveOutputFilename(final String from) {
        final List<String> list = outputFilenames.get();
        for (int i = 0; i < list.size(); i += 2) {
            final String key = list.get(i);
            final String value = list.get(i + 1);
            if (key.equals(from)) {
                return value;
            }
        }
        return from;
    }

    @Override
    protected List<SourceSet> getDependentSourceSets() {
        if (isMainSourceSetCompileTask()) {
            return List.of(getCompileSourceSet());
        }
        return super.getDependentSourceSets();
    }
}
