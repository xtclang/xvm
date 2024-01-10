package org.xtclang.plugin.tasks;

import kotlin.Pair;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.xtclang.plugin.XtcCompilerExtension;
import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.launchers.CommandLine;

import javax.inject.Inject;
import java.nio.file.Files;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.xtclang.plugin.XtcPluginConstants.XTC_COMPILER_CLASS_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_COMPILER_LAUNCHER_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_SOURCE_FILE_EXTENSION;
import static org.xtclang.plugin.XtcProjectDelegate.hasFileExtension;

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
    public XtcCompileTask(final XtcProjectDelegate delegate, final String taskName, final SourceSet sourceSet) {
        super(delegate, taskName, sourceSet);

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

    @InputDirectory
    @PathSensitive(PathSensitivity.ABSOLUTE)
    Provider<Directory> getInputXdkContents() {
        return delegate.getXdkContentsDir();
    }

    @OutputDirectory
    Provider<Directory> getOutputXtcModules() {
        return delegate.getXtcCompilerOutputDirModules(sourceSet);
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

    @OutputDirectory
    Provider<Directory> getOutputDirectory() {
        // TODO We can make this configurable later.
        return delegate.getXtcCompilerOutputDirModules(sourceSet);
    }

    @TaskAction
    public void compile() {
        start();

        final var args = new CommandLine(XTC_COMPILER_CLASS_NAME, getJvmArgs().get());

        if (getForceRebuild().get()) {
            logger.warn("{} WARNING: Force Rebuild was set; touching everything in sourceSet '{}' and its resources.", prefix, sourceSet.getName());
            touchAllSource(); // The source set remains the same, but hopefully doing this "touch" as the first executable action will still be before computation of changes.
        }

        File outputDir = delegate.getXtcCompilerOutputDirModules(sourceSet).get().getAsFile();
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
        // If xtcVersion is set, we stamp that, otherwise we ignore it for now. It may be that we should stamp it
        // as the xcc version used to compile if no flag is given?
        final String moduleVersion = resolveModuleVersion();
        if (moduleVersion != null) {
            if (delegate.hasVerboseLogging()) {
                logger.lifecycle("{} Task '{}' Stamping XTC module with version: '{}'", prefix, getName(), moduleVersion);
            }
            args.add("--set-version", moduleVersion);
        }
        args.addRepeated("-L", resolveXtcModulePath());
        final var sourceFiles = resolveXtcSourceFiles().stream().map(File::getAbsolutePath).toList();
        if (sourceFiles.isEmpty()) {
            logger.warn("{} Task '{}' No source file found for source set: '{}'", prefix, getName(), sourceSet.getName());
        }
        sourceFiles.forEach(args::addRaw);

        final var launcher = createLauncher();
        handleExecResult(delegate.getProject(), launcher.apply(args));
        finalizeOutputs();
        // TODO outputFilename default task property?
    }

    private String resolveModuleVersion() {
        // TODO: We need to tell the plugin, when we build it, which version it has from the catalog. This is actually the XTC artifact that needs to be asked its version. The launcher? the xdk dependency? Figure this one out.
        if (getXtcVersion().isPresent()) {
            return getXtcVersion().get();
        }
        return null;
        // TODO what is correct?
        //final String projectVersion = project.getVersion().toString();
        //logger.warn("{} Task '{}', no xtcVersion property available. Should query XDK dependency for xtcVersion, Defaulting to project version: '{}'", prefix, taskName, projectVersion);
        //return Project.DEFAULT_VERSION.equals(projectVersion) ? null : projectVersion;
    }

    private void finalizeOutputs() {
        delegate.getProject().fileTree(getOutputXtcModules()).filter(delegate::isXtcBinary).forEach(oldFile -> {
            final String oldName = oldFile.getName();
            final String newName = resolveOutputFilename(oldName);
            final File newFile;
            if (oldName.equals(newName)) {
                newFile = oldFile;
                logger.info("{} Finalizing compiler output XTC binary filename: '{}'", prefix, oldName);
            } else {
                newFile = new File(oldFile.getParentFile(), newName);
                logger.info("{} Changing and finalizing compiler output XTC filename: '{}' to '{}'", prefix, oldName, newName);
                logger.info("{} File tree scan: {} should be renamed to {}", delegate, oldFile, newFile);
                if (!oldFile.renameTo(newFile)) {
                    // TODO does this update the output? Seems like it. Write a unit test.
                    throw delegate.buildException("Failed to rename " + oldFile + " to " + newFile);
                }
            }
        });
    }

    private Set<File> resolveXtcSourceFiles() {
        final var resolvedSources = getSource().filter(this::isTopLevelXtcSourceFile).getFiles();
        logger.info("{} Resolved top level sources (should be module definitions, or XTC will fail later): {}", prefix, resolvedSources);
        return resolvedSources;
    }

    private Set<File> resolveXtcModulePath() {
        return delegate.resolveModulePath(getName(), getInputDeclaredDependencyModules());
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
        logger.info("{} Checking if {} is a module definition (currently, just checking if it's a top level file): {}", prefix, file.getAbsolutePath(), isTopLevelSrc);
        if (isTopLevelSrc || "mack.x".equalsIgnoreCase(file.getName())) {
            logger.info("{} Found module definition: {}", prefix, file.getAbsolutePath());
            return true;
        }
        return false;
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
}

