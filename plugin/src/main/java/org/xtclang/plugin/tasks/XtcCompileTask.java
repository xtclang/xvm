package org.xtclang.plugin.tasks;

import static org.xtclang.plugin.XtcPluginConstants.XTC_COMPILER_CLASS_NAME;
import static org.xtclang.plugin.XtcPluginUtils.FileUtils.isValidXtcModuleSafe;
import static org.xtclang.plugin.XtcPluginUtils.failure;

import java.io.File;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import kotlin.Pair;

import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileTree;
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

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcCompilerExtension;
import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.launchers.AttachedStrategy;
import org.xtclang.plugin.launchers.DirectStrategy;
import org.xtclang.plugin.launchers.ExecutionMode;
import org.xtclang.plugin.launchers.ExecutionStrategy;

@CacheableTask
public abstract class XtcCompileTask extends XtcSourceTask implements XtcCompilerExtension {
    // Default values for inputs and outputs below are inherited from extension, can be reset per task
    protected final Property<@NotNull Boolean> disableWarnings;
    protected final Property<@NotNull Boolean> strict;
    protected final Property<@NotNull Boolean> hasQualifiedOutputName;
    protected final Property<@NotNull Boolean> hasVersionedOutputName;
    protected final Property<@NotNull String> xtcVersion;
    protected final Property<@NotNull Boolean> rebuild;

    // Per-task configuration properties only.
    private final ListProperty<@NotNull String> outputFilenames;
    
    // Configuration-time captured data to avoid Project references during execution
    //private final Provider<@NotNull Directory> projectDir;
    private final String sourceSetName;
    private final Directory resourceDir;
    private final Directory outputDir;
    private final Set<File> sourceSetDirs;

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
    @SuppressWarnings("ConstructorNotProtectedInAbstractClass")
    @Inject
    public XtcCompileTask(final ObjectFactory objects, final Project project, final SourceSet sourceSet) {
        // the compile task name should be determined by its source set
        // a runtime task can by default depend on all source sets, it's fine. even a test task.
        super(objects, project);

        // The outputFilenames property only exists in the compile task, not in the compile configuration.
        this.outputFilenames = objects.listProperty(String.class).value(new ArrayList<>());

        // Capture all necessary data at configuration time to avoid Project references during execution
        // this.projectDir = objects.directoryProperty().value(project.getLayout().getProjectDirectory());

        // Capture source set data at configuration time to avoid Project references during execution
        this.sourceSetName = sourceSet.getName();
        this.resourceDir = XtcProjectDelegate.getXtcResourceOutputDirectory(project, sourceSet).get();
        this.outputDir = XtcProjectDelegate.getXtcSourceSetOutputDirectory(project, sourceSet).get();
        this.sourceSetDirs = sourceSet.getAllSource().getSrcDirs();

        // Conventions inherited from extension; can be reset on a per-task basis, of course.
        this.disableWarnings = objects.property(Boolean.class).convention(ext.getDisableWarnings());
        this.strict = objects.property(Boolean.class).convention(ext.getStrict());
        this.hasQualifiedOutputName = objects.property(Boolean.class).convention(ext.getQualifiedOutputName());
        this.hasVersionedOutputName = objects.property(Boolean.class).convention(ext.getVersionedOutputName());
        this.rebuild = objects.property(Boolean.class).convention(ext.getRebuild());
        this.xtcVersion = objects.property(String.class).convention(ext.getXtcVersion());
    }

    @Internal
    public String getCompileSourceSetName() {
        return sourceSetName;
    }

    private boolean isMainSourceSetCompileTask() {
        return SourceSet.MAIN_SOURCE_SET_NAME.equals(getCompileSourceSetName());
    }

    // TODO Why do we even have these internals?
    @Internal
    public Directory getOutputDirectoryInternal() {
        return outputDir;
    }

    @Internal
    public Directory getResourceDirectoryInternal() {
        return resourceDir;
    }

    public String resolveXtcVersion() {
        if (getXtcVersion().isPresent()) {
            return getXtcVersion().get();
        }
        throw failure("resolveXtcVersion returned null, no XTC version configured.");
    }

    public static String semanticVersion(final String version) {
        return version.endsWith("-SNAPSHOT") ? version.replace("-SNAPSHOT", "+SNAPSHOT") : version;
    }

    public Set<File> resolveXtcSourceFiles() {
        final var resolvedSources = getSource().filter(this::isTopLevelXtcSourceFile).getFiles();
        logger.info("[plugin] Resolved top level sources (should be module definitions, or XTC will fail later): {}", resolvedSources);
        return resolvedSources;
    }

    // There is one source set to compile, but there other may be needed for the module path.
    // The compileXXXXtc task should only use XXX as sources, but the module path for compilation
    // should point out the other source sets as well, for example for a test task, but not for the
    // main task. I suppose the most generic way to do this is to be able to add a source set to the
    // module path in the task, but let's try to work incrementally.

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
    public void outputFilename(final Provider<@NotNull String> from, final Provider<@NotNull String> to) {
        outputFilenames.add(from);
        outputFilenames.add(to);
    }

    @Input
    ListProperty<@NotNull String> getOutputFilenames() {
        return outputFilenames;
    }

    @Input
    @Override
    public Property<@NotNull Boolean> getQualifiedOutputName() {
        return hasQualifiedOutputName;
    }

    @Input
    @Override
    public Property<@NotNull Boolean> getVersionedOutputName() {
        return hasVersionedOutputName;
    }

    @Optional
    @Input
    @Override
    public Property<@NotNull String> getXtcVersion() {
        return xtcVersion;
    }

    @Input
    @Override
    public Property<@NotNull Boolean> getStrict() {
        return strict;
    }

    @Input
    @Override
    public Property<@NotNull Boolean> getRebuild() {
        return rebuild;
    }

    @Input
    @Override
    public Property<@NotNull Boolean> getDisableWarnings() {
        return disableWarnings;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    Provider<@NotNull Directory> getResourceDirectory() {
        // TODO: This is wrong. The compile task should not be the one depending on resources src, but resources build.
        //   But that is java behavior, so make sure at least we get the resource input dependency.
        return objects.directoryProperty().value(getResourceDirectoryInternal());
    }

    @OutputDirectory
    Provider<@NotNull Directory> getOutputDirectory() {
        // TODO We can make this configurable later.
        return objects.directoryProperty().value(getOutputDirectoryInternal());
    }

    /**
     * Returns all XTC source files (including subdirectory files) for incremental build tracking.
     * This ensures Gradle detects changes in subdirectory .x files that are part of the module
     * compilation unit, even though only top-level module files are passed to the XTC compiler.
     * <p>
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

        // Create and execute the compile strategy (builds CompilerOptions internally after javatools is loaded)
        final var strategy = createCompileStrategy();
        final int exitCode = strategy.execute(this);
        if (exitCode != 0) {
            throw failure("Compilation failed with exit code: {}", exitCode);
        }
        finalizeOutputs();
    }

    private ExecutionStrategy createCompileStrategy() {
        final ExecutionMode mode = getExecutionMode().get();
        return switch (mode) {
            case DIRECT -> new DirectStrategy(logger);
            case ATTACHED -> new AttachedStrategy<>(logger, resolveJavaExecutable());
            case DETACHED -> throw new UnsupportedOperationException("DETACHED mode not supported for compile tasks");
        };
    }

    private String resolveJavaExecutable() {
        final String executable = toolchainExecutable.getOrNull();
        if (executable == null) {
            throw failure("Java toolchain not configured - cannot resolve java executable for forked compilation");
        }
        return executable;
    }

    /**
     * Rename all valid XTC modules in the compile output to their final names, if such names are specified
     * in the DSL for the compile task (the outputFilenames property with its value pairs).
     */
    private void finalizeOutputs() {
        objects.fileTree().from(getOutputDirectory()).filter(f -> isValidXtcModuleSafe(f, logger)).getFiles().forEach(this::renameOutput);
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
            logger.info("[plugin] Renamed and finalized compiler output XTC filename: '{}' to '{}' (path: '{}')", oldName, newName, path.getAbsolutePath());
            return;
        }
        throw failure("Failed to rename '{}' to '{}'. Output file already exists and could not be deleted: '{}'", oldFile, newFile, newFile.getAbsoluteFile());
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
        return sourceSetDirs;
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
