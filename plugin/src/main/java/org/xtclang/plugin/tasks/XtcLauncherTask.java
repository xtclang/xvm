package org.xtclang.plugin.tasks;

import static java.util.Objects.requireNonNull;

import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_CONTENTS;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_JAVATOOLS_INCOMING;
import static org.xtclang.plugin.XtcPluginConstants.XTC_CONFIG_NAME_MODULE_DEPENDENCY;
import static org.xtclang.plugin.XtcPluginConstants.XTC_LANGUAGE_NAME;
import static org.xtclang.plugin.XtcPluginUtils.FileUtils.isValidXtcModuleSafe;
import static org.xtclang.plugin.XtcPluginUtils.argumentArrayToList;
import static org.xtclang.plugin.XtcPluginUtils.capitalize;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.internal.GradlePhaseAssertions;
import org.xtclang.plugin.launchers.JavaClasspathLauncher;
import org.xtclang.plugin.launchers.JavaExecLauncher;
import org.xtclang.plugin.launchers.NativeBinaryLauncher;
import org.xtclang.plugin.launchers.XtcLauncher;

/**
 * Abstract class that represents and XTC Launcher execution (i.e. Compiler, Runner, Disassembler etc.),
 * anything that goes through the XTC Launcher to spawn or call different processes
 */
public abstract class XtcLauncherTask<E extends XtcLauncherTaskExtension> extends XtcDefaultTask implements XtcLauncherTaskExtension {

    // All inherited from launcher task extension and turned into input
    protected final ConfigurableFileCollection modulePath;
    protected final Property<@NotNull InputStream> stdin;
    protected final Property<@NotNull OutputStream> stdout;
    protected final Property<@NotNull OutputStream> stderr;
    protected final ListProperty<@NotNull String> jvmArgs;
    protected final Property<@NotNull Boolean> verbose;
    protected final Property<@NotNull Boolean> showVersion;
    protected final Property<@NotNull Boolean> useNativeLauncher;

    protected final E ext;

    // Captured at configuration time for configuration cache compatibility
    protected final Provider<@NotNull Directory> projectDirectory;
    protected final Provider<@NotNull Directory> xdkContentsDir;
    protected final Provider<org.gradle.api.file.@NotNull FileTree> xdkFileTree;
    protected final Map<String, Provider<@NotNull Directory>> sourceSetOutputDirs;
    protected final List<String> sourceSetNames;
    protected final Provider<@NotNull FileCollection> javaToolsConfig;
    protected final Provider<@NotNull FileCollection> xtcModuleDependencies;

    // Resolved launcher configuration as Providers for lazy evaluation.
    // Using Provider ensures values are resolved at execution time, allowing configuration
    // via tasks.configureEach {} to work correctly. Configuration cache compatible.
    protected final Provider<@NotNull Boolean> resolvedUseNativeLauncher;
    protected final Provider<@NotNull String> toolchainExecutable;
    protected final Provider<@NotNull String> projectVersion;

    @SuppressWarnings("this-escape") // Suppressed because launchers need task reference in constructor
    protected XtcLauncherTask(final Project project, final E ext) {
        super(project);
        
        // Assert that we're in configuration phase during task construction
        GradlePhaseAssertions.assertProjectAccessDuringConfiguration(project, "XtcLauncherTask construction");
        this.ext = ext;

        // Capture at configuration time
        this.projectDirectory = objects.directoryProperty().value(project.getLayout().getProjectDirectory());
        this.xdkContentsDir = XtcProjectDelegate.getXdkContentsDir(project);
        this.xdkFileTree = xdkContentsDir.map(dir -> {
            final var tree = objects.fileTree();
            tree.setDir(dir);
            return tree;
        });

        final var sourceSets = XtcProjectDelegate.getSourceSets(project);
        this.sourceSetNames = sourceSets.stream().map(SourceSet::getName).toList();
        this.sourceSetOutputDirs = sourceSets.stream()
            .collect(Collectors.toMap(SourceSet::getName,
                sourceSet -> XtcProjectDelegate.getXtcSourceSetOutputDirectory(project, sourceSet)
            ));


        this.modulePath = objects.fileCollection().from(ext.getModulePath());

        this.stdin = objects.property(InputStream.class);
        this.stdout = objects.property(OutputStream.class);
        this.stderr = objects.property(OutputStream.class);

        if (ext.getStdin().isPresent()) {
            stdin.set(ext.getStdin());
        }
        if (ext.getStdout().isPresent()) {
            stdout.set(ext.getStdout());
        }
        if (ext.getStderr().isPresent()) {
            stderr.set(ext.getStderr()); // TODO maybe rename the properties to standardOutput, errorOutput etc to conform to Gradle name standard. Right now
            // we clearly want them to be separated from any defaults, though, so we know our launcher tasks pick the correct configured streams.
        }

        this.jvmArgs = objects.listProperty(String.class).convention(ext.getJvmArgs());

        this.verbose = objects.property(Boolean.class).convention(ext.getVerbose());
        this.showVersion = objects.property(Boolean.class).convention(ext.getShowVersion());
        this.useNativeLauncher = objects.property(Boolean.class).convention(ext.getUseNativeLauncher());

        // Capture as Providers for lazy evaluation - allows configuration via tasks.configureEach {}
        this.resolvedUseNativeLauncher = ext.getUseNativeLauncher();

        // Assert that required configurations exist - they should always be created by this plugin
        final var configurations = project.getConfigurations();
        assert configurations.findByName(XDK_CONFIG_NAME_JAVATOOLS_INCOMING) != null : 
            "Configuration '" + XDK_CONFIG_NAME_JAVATOOLS_INCOMING + "' must exist - it should be created by XTC plugin during project configuration";
        
        this.javaToolsConfig = project.provider(() -> objects.fileCollection().from(configurations.getByName(XDK_CONFIG_NAME_JAVATOOLS_INCOMING)));
        
        // Build XTC module dependencies from all source sets - assert they exist too  
        this.xtcModuleDependencies = project.provider(() -> {
            FileCollection xtcModuleDeps = objects.fileCollection();
            for (final String sourceSetName : sourceSetNames) {
                final String configName = XtcProjectDelegate.incomingXtcModuleDependencies(sourceSetName);
                assert configurations.findByName(configName) != null : 
                    "Configuration '" + configName + "' must exist for sourceSet '" + sourceSetName + "' - it should be created by XTC plugin during source set configuration";
                xtcModuleDeps = xtcModuleDeps.plus(objects.fileCollection().from(configurations.getByName(configName)));
            }
            return xtcModuleDeps;
        });
        
        // Capture JavaExecLauncher configuration at configuration time
        this.toolchainExecutable = project.provider(() -> {
            final var javaExtension = project.getExtensions().findByType(org.gradle.api.plugins.JavaPluginExtension.class);
            if (javaExtension != null) {
                final var toolchains = project.getExtensions().getByType(org.gradle.jvm.toolchain.JavaToolchainService.class);
                final var launcher = toolchains.launcherFor(javaExtension.getToolchain());
                return launcher.get().getExecutablePath().toString();
            }
            return null;
        });
        this.projectVersion = project.provider(() -> project.getVersion().toString());
        
        // Validate configuration-time captures for configuration cache compatibility
        GradlePhaseAssertions.validateConfigurationTimeCapture(this.xdkContentsDir, "XDK contents directory");
        GradlePhaseAssertions.validateConfigurationTimeCapture(this.sourceSetNames, "source set names");
        GradlePhaseAssertions.validateConfigurationTimeCapture(this.sourceSetOutputDirs, "source set output directories");
    }

    @Inject
    public abstract ExecOperations getExecOperations();

    @Override
    public void executeTask() {
        // Assert that we're in execution phase during task execution
        GradlePhaseAssertions.assertExecutionPhase(this, "XtcLauncherTask execution");
        super.executeTask();
    }

    @Override
    public boolean hasVerboseLogging() {
        return super.hasVerboseLogging() || verbose.get();
    }

    @Internal
    protected E getExtension() {
        return ext;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    FileCollection getInputXtcJavaToolsConfig() {
        return javaToolsConfig.get();
    }

    /**
     * Returns the javatools classpath for use by launchers.
     * This is used by the compiler daemon to load the XTC compiler.
     */
    @Internal
    public FileCollection getJavaToolsClasspath() {
        return javaToolsConfig.get();
    }

    public boolean hasStdinRedirect() {
        return stdin.isPresent();
    }

    public boolean hasStdoutRedirect() {
        return stdout.isPresent();
    }

    public boolean hasStderrRedirect() {
        return stderr.isPresent();
    }

    @SuppressWarnings("unused")
    public boolean hasOutputRedirects() {
        return hasStdoutRedirect() || hasStderrRedirect();
    }

    @Internal  // Streams are not serializable for configuration cache
    public Property<@NotNull InputStream> getStdin() {
        return stdin;
    }

    @Internal  // Streams are not serializable for configuration cache
    public Property<@NotNull OutputStream> getStdout() {
        return stdout;
    }

    @Internal  // Streams are not serializable for configuration cache
    public Property<@NotNull OutputStream> getStderr() {
        return stderr;
    }

    @Override
    public void jvmArg(final Provider<? extends @NotNull String> arg) {
        // Use objects factory instead of Project to create provider
        jvmArgs(objects.listProperty(String.class).value(arg.map(Collections::singletonList)));
    }

    @Override
    public void jvmArgs(final String... args) {
        jvmArgs.addAll(argumentArrayToList(args));
    }

    @Override
    public void jvmArgs(final Iterable<? extends String> args) {
        jvmArgs.addAll(args);
    }

    @Override
    public void jvmArgs(final Provider<? extends @NotNull Iterable<? extends String>> provider) {
        jvmArgs.addAll(provider);
    }

    @Override
    public void setJvmArgs(final Iterable<? extends String> elements) {
        jvmArgs.set(elements);
    }

    @Override
    public void setJvmArgs(final Provider<? extends @NotNull Iterable<? extends String>> provider) {
        jvmArgs.set(provider);
    }

    @Input
    public Property<@NotNull Boolean> getVerbose() {
        return verbose;
    }


    @Input
    public Property<@NotNull Boolean> getShowVersion() {
        return showVersion;
    }

    /**
     * Returns the useNativeLauncher property for DSL configuration.
     * This property allows users to configure whether to use the native launcher.
     * Note: Execution logic uses {@link #getUseNativeLauncherValue()} which is captured at configuration time.
     */
    @Input
    public Property<@NotNull Boolean> getUseNativeLauncher() {
        return useNativeLauncher;
    }

    @Input
    @Override
    public Property<@NotNull Boolean> getFork() {
        return ext.getFork();
    }

    @Optional
    @Input
    public ListProperty<@NotNull String> getJvmArgs() {
        return jvmArgs;
    }

    /**
     * Returns the resolved launcher type as a lazily evaluated value.
     * This allows configuration via tasks.configureEach {} to work correctly.
     * Configuration cache compatible.
     * See {@link #getUseNativeLauncher()} for the DSL-configurable Property version.
     */
    @Input
    public boolean getUseNativeLauncherValue() {
        return resolvedUseNativeLauncher.get();
    }


    /**
     * Returns the source set names captured at configuration time.
     * This is an input because changes to source sets affect the module path.
     */
    @Input
    public List<String> getSourceSetNames() {
        return sourceSetNames;
    }

    @Internal
    public abstract String getJavaLauncherClassName();

    @Internal
    public abstract String getNativeLauncherCommandName();

    protected <R extends ExecResult> R handleExecResult(final R result) {
        final int exitValue = result.getExitValue();
        if (exitValue != 0) {
            final String taskName = getName();
            final String launcherType;
            if (getUseNativeLauncherValue()) {
                launcherType = "Native";
            } else {
                launcherType = "JavaClasspath (fork=" + ext.getFork().get() + ")";
            }
            logger.error("""

                [plugin] ==============================================================================
                [plugin] XTC Compilation Failed
                [plugin] ==============================================================================
                [plugin] Task:        {}
                [plugin] Launcher:    {}
                [plugin] Exit Code:   {}
                [plugin] ------------------------------------------------------------------------------
                [plugin] The XTC compiler process terminated with a non-zero exit code ({}).
                [plugin] This typically indicates compilation errors in your XTC source files.
                [plugin] Review the compiler output above for specific error messages.
                [plugin] ==============================================================================
                """, taskName, launcherType, exitValue, exitValue);
        }
        result.rethrowFailure();
        result.assertNormalExitValue();
        return result;
    }

    protected XtcLauncher<E, ? extends XtcLauncherTask<E>> createLauncher() {
        // Ensure javatools.jar is loaded into the plugin's classloader before creating any launcher
        // that might reference javatools types. This makes all javatools classes available throughout
        // the plugin without needing reflection or special classloader handling.
        org.xtclang.plugin.XtcJavaToolsRuntime.ensureJavaToolsInClasspath(
            projectVersion, javaToolsConfig, xdkFileTree, logger);

        if (getUseNativeLauncherValue()) {
            logger.info("[plugin] Using native binary launcher");
            return new NativeBinaryLauncher<>(this, logger, getExecOperations());
        }

        final boolean fork = ext.getFork().get();
        if (fork) {
            logger.lifecycle("[plugin] Using JavaClasspathLauncher with fork=true (separate process)");
        } else {
            logger.lifecycle("[plugin] Using JavaClasspathLauncher with fork=false (in-process execution)");
        }

        return new JavaClasspathLauncher<>(this, logger,
            projectVersion, xdkFileTree, javaToolsConfig, toolchainExecutable,
            projectDirectory.get().getAsFile(), fork);
    }

    protected List<File> resolveFullModulePath() {
        final var map = new HashMap<String, Set<File>>();

        final Set<File> xdkContents = resolveDirectories(xdkContentsDir);
        map.put(XDK_CONFIG_NAME_CONTENTS, xdkContents);

        // If custom module path is specified, use it instead of xtcModule dependencies
        // This supports aggregator projects that collect modules in a custom location
        if (!modulePath.isEmpty()) {
            final Set<File> customModulePath = resolveAsDirectories(modulePath);
            map.put("customModulePath", customModulePath);
        } else {
            // Use xtcModule dependencies only when no custom module path is set
            final Set<File> xtcModuleDeclarations = resolveFiles(getXtcModuleDependencies());
            map.put(XTC_CONFIG_NAME_MODULE_DEPENDENCY, xtcModuleDeclarations);
        }

        for (final var entry : sourceSetOutputDirs.entrySet()) {
            final String sourceSetName = entry.getKey();
            final Provider<@NotNull Directory> outputDir = entry.getValue();
            final Set<File> sourceSetOutput = resolveDirectories(outputDir);
            map.put(XTC_LANGUAGE_NAME + capitalize(sourceSetName), sourceSetOutput);
        }

        logger.info("[plugin] Compilation/runtime full module path resolved as: ");
        map.forEach((k, v) -> logger.info("[plugin]     Resolved files: {} -> {}", k, v));
        return verifiedModulePath(map);
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    Provider<@NotNull Directory> getInputXdkContents() {
        return xdkContentsDir;
    }

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    FileCollection getXtcModuleDependencies() {
        return xtcModuleDependencies.get();
    }

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @Override
    public ConfigurableFileCollection getModulePath() {
        return modulePath;
    }

    @Internal
    protected List<SourceSet> getDependentSourceSets() {
        // This method still returns SourceSet objects for compatibility, but they are reconstructed from names
        // TODO: Eventually replace this with a method that returns source set names only
        throw new UnsupportedOperationException("getDependentSourceSets() should not be called at execution time for configuration cache compatibility. Use sourceSetNames instead.");
    }

    protected List<File> verifiedModulePath(final Map<String, Set<File>> map) {
        logger.info("[plugin] ModulePathMap: [{} keys and {} values]", map.size(), map.values().stream().mapToInt(Set::size).sum());

        final var modulePathList = new ArrayList<File>();
        map.forEach((provider, files) -> {
            logger.info("[plugin]     Module path from: '{}':", provider);
            if (files.isEmpty()) {
                logger.info("[plugin]         (empty)");
            }
            files.forEach(f -> logger.info("[plugin]         {}", f.getAbsolutePath()));

            modulePathList.addAll(files.stream().filter(f -> {
                if (f.isDirectory()) {
                    logger.info("[plugin] Adding directory to module path ({}).", f.getAbsolutePath());
                } else if (!isValidXtcModuleSafe(f, logger)) {
                    logger.warn("[plugin] Has a non .xtc module file on the module path ({}). Was this intended?", f.getAbsolutePath());
                    return false;
                }
                return true;
            }).toList());
        });

        final Set<File> modulePathSet = modulePathList.stream().collect(Collectors.toUnmodifiableSet());
        final int modulePathListSize = modulePathList.size();
        final int modulePathSetSize = modulePathSet.size();

        // Check that we don't have name collisions with the same dependency declared in several places.
        if (modulePathListSize != modulePathSetSize) {
            logger.warn("[plugin] There are {} duplicated modules on the full module path.", modulePathListSize - modulePathSetSize);
        }

        checkDuplicatesInModulePaths(modulePathSet);

        // Check that all modules on path are XTC files.
        logger.info("[plugin] Final module path: {}", modulePathSet);
        // We sort the module path on File.compareTo, to make it deterministic between configurations.
        return modulePathSet.stream().sorted().toList();
    }

    private static void checkDuplicatesInModulePaths(final Set<File> modulePathSet) {
        for (final File module : modulePathSet) {
            // find modules with the same name (or TODO: with the same identity)
            if (module.isDirectory()) {
                // TODO, sanity check directories later. The only cause of concern are identical ones, and that is not fatal, but may merit a warning.
                //  The Set data structure already takes care of silently removing them, however.
                continue;
            }
            final List<File> dupes = modulePathSet.stream().filter(File::isFile).filter(f -> f.getName().equals(module.getName())).toList();
            assert !dupes.isEmpty();
            if (dupes.size() != 1) {
                throw new GradleException("[plugin] A dependency with the same name is defined in more than one (" + dupes.size() + ") location on the module path.");
            }
        }
    }

    public static Set<File> resolveFiles(final FileCollection files) {
        return files.isEmpty() ? Collections.emptySet() : files.getAsFileTree().getFiles();
    }

    public static Set<File> resolveDirectories(final Set<File> files) {
        return files.stream().map(f -> requireNonNull(f.getParentFile())).collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Resolves a FileCollection to a set of directories, preserving directories as-is
     * instead of expanding them to their contents. For files, returns their parent directory.
     */
    public static Set<File> resolveAsDirectories(final FileCollection files) {
        if (files.isEmpty()) {
            return Collections.emptySet();
        }
        return files.getFiles().stream()
                .map(f -> f.isDirectory() ? f : requireNonNull(f.getParentFile()))
                .collect(Collectors.toUnmodifiableSet());
    }

    @SuppressWarnings("unused")
    protected Set<File> resolveFiles(final Provider<@NotNull Directory> dirProvider) {
        return resolveFiles(objects.fileCollection().from(dirProvider));
    }

    protected Set<File> resolveDirectories(final Provider<@NotNull Directory> dirProvider) {
        return resolveDirectories(resolveFiles(objects.fileCollection().from(dirProvider)));
    }

    protected final List<String> resolveJvmArgs() {
        final var list = new ArrayList<>(getJvmArgs().get());
        // Debug arguments are now specified via jvmArgs() - use standard JDWP format:
        // jvmArgs("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
        return Collections.unmodifiableList(list);
    }

}
