package org.xtclang.plugin.tasks;

import static org.xtclang.plugin.XtcPluginConstants.XTC_COMPILER_CLASS_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_COMPILER_LAUNCHER_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_COMPILE_MAIN_TASK_NAME;
import static org.xtclang.plugin.XtcPluginUtils.FileUtils.isValidXtcModuleSafe;

import java.io.File;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import kotlin.Pair;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileTree;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
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

@CacheableTask
public abstract class XtcCompileTask extends XtcSourceTask implements XtcCompilerExtension {
    // Default values for inputs and outputs below are inherited from extension, can be reset per task
    protected final Property<Boolean> disableWarnings;
    protected final Property<Boolean> strict;
    protected final Property<Boolean> hasQualifiedOutputName;
    protected final Property<Boolean> hasVersionedOutputName;
    protected final Property<String> xtcVersion;
    protected final Property<Boolean> rebuild;

    // Per-task configuration properties only.
    private final ListProperty<String> outputFilenames;
    
    // Configuration-time captured data to avoid Project references during execution
    private final Provider<Directory> projectDirectory;
    private volatile String cachedCompileSourceSetName;
    private volatile Directory cachedResourceDirectory;
    private volatile Directory cachedOutputDirectory;
    private volatile Set<File> cachedSourceDirectories;

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
    public XtcCompileTask(final Project project, final SourceSet sourceSet) {
        // the compile task name should be determined by its source set
        // a runtime task can by default depend on all source sets, it's fine. even a test task.
        super(project);

        // The outputFilenames property only exists in the compile task, not in the compile configuration.
        this.outputFilenames = objects.listProperty(String.class).value(new ArrayList<>());
        
        // Capture all necessary data at configuration time to avoid Project references during execution
        this.projectDirectory = objects.directoryProperty().value(project.getLayout().getProjectDirectory());
        
        // Capture source set data at configuration time to avoid Project references during execution
        this.cachedCompileSourceSetName = sourceSet.getName();
        this.cachedResourceDirectory = XtcProjectDelegate.getXtcResourceOutputDirectory(project, sourceSet).get();
        this.cachedOutputDirectory = XtcProjectDelegate.getXtcSourceSetOutputDirectory(project, sourceSet).get();
        this.cachedSourceDirectories = sourceSet.getAllSource().getSrcDirs();

        // Conventions inherited from extension; can be reset on a per-task basis, of course.
        this.disableWarnings = objects.property(Boolean.class).convention(ext.getDisableWarnings());
        this.strict = objects.property(Boolean.class).convention(ext.getStrict());
        this.hasQualifiedOutputName = objects.property(Boolean.class).convention(ext.getQualifiedOutputName());
        this.hasVersionedOutputName = objects.property(Boolean.class).convention(ext.getVersionedOutputName());
        this.rebuild = objects.property(Boolean.class).convention(ext.getRebuild());
        this.xtcVersion = objects.property(String.class).convention(ext.getXtcVersion());
    }


    private static String calculateSourceSetName(final String taskName) {
        if (XTC_COMPILE_MAIN_TASK_NAME.equals(taskName)) {
            return SourceSet.MAIN_SOURCE_SET_NAME;
        }
        final int compileIndex = taskName.indexOf("compile");
        final int xtcIndex = taskName.indexOf("Xtc");
        if (compileIndex == -1 || xtcIndex == -1) {
            throw new GradleException("Failed to resolve source set name from compile task '" + taskName + "'");
        }
        final String sourceSetName = taskName.substring(compileIndex + "compile".length(), xtcIndex);
        return sourceSetName.toLowerCase();
    }

    private String getCompileSourceSetName() {
        return cachedCompileSourceSetName;
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
     */
    @SuppressWarnings("unused") // No, IntelliJ. It's not.
    public void outputFilename(final String from, final String to) {
        outputFilenames.add(from);
        outputFilenames.add(to);
    }

    @SuppressWarnings("unused") // No, IntelliJ. It's not.
    public void outputFilename(final Pair<String, String> pair) {
        outputFilenames.add(pair.getFirst());
        outputFilenames.add(pair.getSecond());
    }

    @SuppressWarnings("unused") // No, IntelliJ. It's not.
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
        return strict;
    }

    @Input
    @Override
    public Property<Boolean> getRebuild() {
        return rebuild;
    }

    @Input
    @Override
    public Property<Boolean> getDisableWarnings() {
        return disableWarnings;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    Provider<Directory> getResourceDirectory() {
        // TODO: This is wrong. The compile task should not be the one depending on resources src, but resources build.
        //   But that is java behavior, so make sure at least we get the resource input dependency.
        return objects.directoryProperty().value(getResourceDirectoryInternal());
    }
    
    private Directory getResourceDirectoryInternal() {
        return cachedResourceDirectory;
    }

    @OutputDirectory
    Provider<Directory> getOutputDirectory() {
        // TODO We can make this configurable later.
        return objects.directoryProperty().value(getOutputDirectoryInternal());
    }
    
    private Directory getOutputDirectoryInternal() {
        return cachedOutputDirectory;
    }

    /**
     * Returns all XTC source files (including subdirectory files) for incremental build tracking.
     * This ensures Gradle detects changes in subdirectory .x files that are part of the module
     * compilation unit, even though only top-level module files are passed to the XTC compiler.
     * 
     * This method fixes the incremental build issue where changes to subdirectory .x files 
     * (like xenia/BundlePool.x) were not triggering recompilation of the parent module.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileTree getAllXtcSourceFiles() {
        return getSource(); // All .x files in the source tree for dependency tracking
    }

    @TaskAction
    @Override
    public void executeTask() {
        super.executeTask();

        final var sourceSetName = getCompileSourceSetName();
        final var args = new CommandLine(XTC_COMPILER_CLASS_NAME, resolveJvmArgs());

        final File outputDir = getOutputDirectoryInternal().getAsFile();
        args.add("-o", projectDirectory.get().getAsFile().toPath().relativize(outputDir.toPath()).toString());

        logger.info("[plugin] Output directory for {} is : {}", sourceSetName, outputDir);
        final var processedResourcesDir = getResourceDirectoryInternal().getAsFile();
        logger.info("[plugin] Resolving resource dir (build): '{}'.", processedResourcesDir);
        if (processedResourcesDir.exists()) {
            logger.info("[plugin] '{}' Added as resource directory for '{}'.", processedResourcesDir.getAbsolutePath(), getName());
            args.add("-r", projectDirectory.get().getAsFile().toPath().relativize(processedResourcesDir.toPath()).toString());
        }

        args.addBoolean("--version", getShowVersion().get());
        args.addBoolean("--rebuild", getRebuild().get());
        args.addBoolean("--nowarn", getDisableWarnings().get());
        args.addBoolean("--verbose", getVerbose().get());
        args.addBoolean("--strict", getStrict().get());
        args.addBoolean("--qualify", getQualifiedOutputName().get());

        // If xtcVersion is set, we stamp that, otherwise we ignore it for now. It may be that we should stamp as xcc version used to compile if not given?
        final String moduleVersion = resolveModuleVersion();
        if (moduleVersion != null) {
            if (hasVerboseLogging()) {
                logger.lifecycle("[plugin] Stamping XTC module with version: '{}'", moduleVersion);
            }
            args.add("--set-version", semanticVersion(moduleVersion));
        }
        args.addRepeatedRelative("-L", resolveFullModulePath(), projectDirectory.get().getAsFile());
        final var sourceFiles = resolveXtcSourceFiles().stream()
                .map(file -> projectDirectory.get().getAsFile().toPath().relativize(file.toPath()).toString())
                .sorted().toList();
        if (sourceFiles.isEmpty()) {
            logger.warn("[plugin] No source file found for source set: '{}'", sourceSetName);
        }
        sourceFiles.forEach(args::addRaw);

        final var launcher = createLauncher();
        handleExecResult(launcher.apply(args));
        finalizeOutputs();
    }

    private static String semanticVersion(final String version) {
        return version.endsWith("-SNAPSHOT") ? version.replace("-SNAPSHOT", "+SNAPSHOT") : version;
    }

    private String resolveModuleVersion() {
        // TODO: We need to tell the plugin, when we build it, which version it has from the catalog.
        //    This is actually the XTC artifact that needs to be asked its version. The launcher? the xdk dependency? Figure this one out.
        if (getXtcVersion().isPresent()) {
            return getXtcVersion().get();
        }
        logger.warn("[plugin] WARNING: No XTC version was resolved. Module will not be versioned.");
        return null;
    }

    /**
     * Rename all valid XTC modules in the compile output to their final names, if such names are specified
     * in the DSL for the compile task (the outputFilenames property with its value pairs).
     */
    private void finalizeOutputs() {
        objects.fileTree().from(getOutputDirectory()).filter(f -> isValidXtcModuleSafe(f, getLogger())).getFiles().forEach(this::renameOutput);
    }

    private void renameOutput(final File oldFile) {
        final String oldName = oldFile.getName();
        final String newName = resolveOutputFilename(oldName);
        if (oldName.equals(newName)) {
            logger.info("[plugin] Finalizing compiler output XTC binary filename: '{}'", oldName);
            return;
        }
        final File path = oldFile.getParentFile();
        final File newFile = new File(path, newName);
        if ((!newFile.exists() || newFile.delete()) && oldFile.renameTo(newFile)) {
            logger.info("[plugin] Renamed and finalized compiler output XTC filename: '{}' to '{}' (path: '{}')",
                    oldName, newName, path.getAbsolutePath());
            return;
        }
        throw new GradleException("Failed to rename '" + oldFile + "' to '" + newFile + "'. Output file already exists and could not be deleted: '" + newFile.getAbsoluteFile() + "'");
    }

    private Set<File> resolveXtcSourceFiles() {
        final var resolvedSources = getSource().filter(file -> isTopLevelXtcSourceFile(file)).getFiles();
        logger.info("[plugin] Resolved top level sources (should be module definitions, or XTC will fail later): {}", resolvedSources);
        return resolvedSources;
    }
    
    private boolean isTopLevelXtcSourceFile(final File file) {
        return !file.isDirectory() && isXtcSourceFile(file) && isTopLevelSource(file);
    }

    private boolean isTopLevelSource(final File file) {
        assert file.isFile();
        final var dir = file.getParentFile();
        assert dir != null && dir.isDirectory();
        final var isTopLevelSrc = getSourceDirectoriesInternal().contains(dir);
        logger.debug("[plugin] Checking if {} is a module definition (currently, just checking if it's a top level file): {}",
            file.getAbsolutePath(), isTopLevelSrc);
        if (isTopLevelSrc || "mack.x".equalsIgnoreCase(file.getName())) {
            logger.info("[plugin] Found module definition: {}", file.getAbsolutePath());
            return true;
        }
        return false;
    }
    
    private Set<File> getSourceDirectoriesInternal() {
        return cachedSourceDirectories;
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
        // Note: The parent implementation now captures source set output directories at configuration time
        // to avoid Project references during execution, so we can safely use it
        return super.getDependentSourceSets().stream()
            .filter(sourceSet -> {
                // For main source set compile tasks, only include the main source set
                if (isMainSourceSetCompileTask()) {
                    return sourceSet.getName().equals(getCompileSourceSetName());
                }
                return true;
            })
            .toList();
    }
}
