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
import org.xtclang.plugin.launchers.BuildThreadLauncher;
import org.xtclang.plugin.launchers.JavaExecLauncher;
import org.xtclang.plugin.launchers.NativeBinaryLauncher;
import org.xtclang.plugin.launchers.XtcLauncher;

/**
 * Abstract class that represents and XTC Launcher execution (i.e. Compiler, Runner, Disassembler etc.),
 * anything that goes through the XTC Launcher to spawn or call different processes
 */
public abstract class XtcLauncherTask<E extends XtcLauncherTaskExtension> extends XtcDefaultTask implements XtcLauncherTaskExtension {

    // All inherited from launcher task extension and turned into input
    protected final Property<@NotNull InputStream> stdin;
    protected final Property<@NotNull OutputStream> stdout;
    protected final Property<@NotNull OutputStream> stderr;
    protected final ListProperty<@NotNull String> jvmArgs;
    protected final Property<@NotNull Boolean> debug;
    protected final Property<@NotNull Integer> debugPort;
    protected final Property<@NotNull Boolean> debugSuspend;
    protected final Property<@NotNull Boolean> verbose;
    protected final Property<@NotNull Boolean> fork;
    protected final Property<@NotNull Boolean> showVersion;
    protected final Property<@NotNull Boolean> useNativeLauncher;

    protected final E ext;
    
    // Configuration-time captured data to avoid Project references during execution
    // These are captured at configuration time to be serializable with configuration cache
    protected final Provider<@NotNull Directory> xdkContentsDirAtConfigurationTime;
    protected final Map<String, Provider<@NotNull Directory>> sourceSetOutputDirsAtConfigurationTime;
    
    // Source set names captured at configuration time to avoid Project access during execution
    protected final List<String> sourceSetNamesAtConfigurationTime;
    
    // Configuration inputs captured as Providers at task creation time - Gradle handles resolution and missing configs
    private final Provider<@NotNull FileCollection> javaToolsConfigAtConfigurationTime;
    private final Provider<@NotNull FileCollection> xtcModuleDependenciesAtConfigurationTime;
    // Launcher configuration captured at configuration time to avoid Project access during execution  
    private final boolean useNativeLauncherAtConfigurationTime;
    private final boolean forkAtConfigurationTime;
    
    // JavaExecLauncher configuration captured at configuration time
    private final Provider<@NotNull String> toolchainExecutableAtConfigurationTime;
    private final Provider<@NotNull String> projectVersionAtConfigurationTime;
    private final Provider<org.gradle.api.file.@NotNull FileTree> xdkFileTreeAtConfigurationTime;

    @SuppressWarnings("this-escape") // Suppressed because launchers need task reference in constructor
    protected XtcLauncherTask(final Project project, final E ext) {
        super(project);
        
        // Assert that we're in configuration phase during task construction
        GradlePhaseAssertions.assertProjectAccessDuringConfiguration(project, "XtcLauncherTask construction");
        this.ext = ext;
        
        // Capture configuration-time data to avoid Project references during execution
        this.xdkContentsDirAtConfigurationTime = XtcProjectDelegate.getXdkContentsDir(project);
        
        // Capture source sets and their output directories at configuration time
        final var sourceSets = XtcProjectDelegate.getSourceSets(project);
        this.sourceSetNamesAtConfigurationTime = sourceSets.stream().map(SourceSet::getName).toList();
        this.sourceSetOutputDirsAtConfigurationTime = sourceSets.stream()
            .collect(Collectors.toMap(SourceSet::getName,
                sourceSet -> XtcProjectDelegate.getXtcSourceSetOutputDirectory(project, sourceSet)
            ));


        this.debug = objects.property(Boolean.class).convention(ext.getDebug());
        this.debugPort = objects.property(Integer.class).convention(ext.getDebugPort());
        this.debugSuspend = objects.property(Boolean.class).convention(ext.getDebugSuspend());

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
        this.fork = objects.property(Boolean.class).convention(ext.getFork());
        this.showVersion = objects.property(Boolean.class).convention(ext.getShowVersion());
        this.useNativeLauncher = objects.property(Boolean.class).convention(ext.getUseNativeLauncher());
        
        // Capture launcher configuration at configuration time to avoid Project access during execution
        this.useNativeLauncherAtConfigurationTime = ext.getUseNativeLauncher().get();
        this.forkAtConfigurationTime = ext.getFork().get();
        
        // Assert that required configurations exist - they should always be created by this plugin
        final var configurations = project.getConfigurations();
        assert configurations.findByName(XDK_CONFIG_NAME_JAVATOOLS_INCOMING) != null : 
            "Configuration '" + XDK_CONFIG_NAME_JAVATOOLS_INCOMING + "' must exist - it should be created by XTC plugin during project configuration";
        
        this.javaToolsConfigAtConfigurationTime = project.provider(() -> objects.fileCollection().from(configurations.getByName(XDK_CONFIG_NAME_JAVATOOLS_INCOMING)));
        
        // Build XTC module dependencies from all source sets - assert they exist too  
        this.xtcModuleDependenciesAtConfigurationTime = project.provider(() -> {
            FileCollection xtcModuleDeps = objects.fileCollection();
            for (final String sourceSetName : sourceSetNamesAtConfigurationTime) {
                final String configName = XtcProjectDelegate.incomingXtcModuleDependencies(sourceSetName);
                assert configurations.findByName(configName) != null : 
                    "Configuration '" + configName + "' must exist for sourceSet '" + sourceSetName + "' - it should be created by XTC plugin during source set configuration";
                xtcModuleDeps = xtcModuleDeps.plus(objects.fileCollection().from(configurations.getByName(configName)));
            }
            return xtcModuleDeps;
        });
        
        // Capture JavaExecLauncher configuration at configuration time
        this.toolchainExecutableAtConfigurationTime = project.provider(() -> {
            final var javaExtension = project.getExtensions().findByType(org.gradle.api.plugins.JavaPluginExtension.class);
            if (javaExtension != null) {
                final var toolchains = project.getExtensions().getByType(org.gradle.jvm.toolchain.JavaToolchainService.class);
                final var launcher = toolchains.launcherFor(javaExtension.getToolchain());
                return launcher.get().getExecutablePath().toString();
            }
            return null;
        });
        this.projectVersionAtConfigurationTime = project.provider(() -> project.getVersion().toString());
        this.xdkFileTreeAtConfigurationTime = XtcProjectDelegate.getXdkContentsDir(project).map(project::fileTree);
        
        // Validate configuration-time captures for configuration cache compatibility
        GradlePhaseAssertions.validateConfigurationTimeCapture(this.xdkContentsDirAtConfigurationTime, "XDK contents directory");
        GradlePhaseAssertions.validateConfigurationTimeCapture(this.sourceSetNamesAtConfigurationTime, "source set names");
        GradlePhaseAssertions.validateConfigurationTimeCapture(this.sourceSetOutputDirsAtConfigurationTime, "source set output directories");
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
        return javaToolsConfigAtConfigurationTime.get();
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

    @Input
    @Override
    public Property<@NotNull Boolean> getDebug() {
        return debug;
    }

    @Input
    @Override
    public Property<@NotNull Integer> getDebugPort() {
        return debugPort;
    }

    @Input
    @Override
    public Property<@NotNull Boolean> getDebugSuspend() {
        return debugSuspend;
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
    public Property<@NotNull Boolean> getFork() {
        return fork;
    }

    @Input
    public Property<@NotNull Boolean> getShowVersion() {
        return showVersion;
    }

    @Input
    public Property<@NotNull Boolean> getUseNativeLauncher() {
        return useNativeLauncher;
    }

    @Optional
    @Input
    public ListProperty<@NotNull String> getJvmArgs() {
        return jvmArgs;
    }

    /**
     * Returns the launcher type configuration captured at configuration time.
     * This is an input because changes to launcher configuration affect execution.
     */
    @Input
    public boolean getUseNativeLauncherAtConfigurationTime() {
        return useNativeLauncherAtConfigurationTime;
    }

    /**
     * Returns the fork configuration captured at configuration time.
     * This is an input because changes to fork configuration affect execution.
     */
    @Input
    public boolean getForkAtConfigurationTime() {
        return forkAtConfigurationTime;
    }

    /**
     * Returns the source set names captured at configuration time.
     * This is an input because changes to source sets affect the module path.
     */
    @Input
    public List<String> getSourceSetNamesAtConfigurationTime() {
        return sourceSetNamesAtConfigurationTime;
    }

    @Internal
    public abstract String getJavaLauncherClassName();

    @Internal
    public abstract String getNativeLauncherCommandName();

    protected <R extends ExecResult> R handleExecResult(final R result) {
        final int exitValue = result.getExitValue();
        if (exitValue != 0) {
            final String taskName = getName();
            final String launcherType = useNativeLauncherAtConfigurationTime ? "Native" :
                                       forkAtConfigurationTime ? "JavaExec" : "BuildThread";
            final var logger = getLogger();
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
        // Create launcher on-demand using captured configuration - NO getProject() calls during execution
        if (useNativeLauncherAtConfigurationTime) {
            getLogger().info("[plugin] Created XTC launcher: native executable.");
            return new NativeBinaryLauncher<>(this, getLogger(), getExecOperations());
        } else if (forkAtConfigurationTime) {
            getLogger().info("[plugin] Created XTC launcher: Java process forked from build.");
            return new JavaExecLauncher<>(this, getLogger(), getExecOperations(),
                toolchainExecutableAtConfigurationTime, projectVersionAtConfigurationTime, xdkFileTreeAtConfigurationTime, javaToolsConfigAtConfigurationTime);
        } else {
            getLogger().warn("{} WARNING: Created XTC launcher: Running launcher in the same thread as the build process. This is not recommended for production",
                    "[plugin]");
            return new BuildThreadLauncher<>(this, getLogger());
        }
    }

    protected List<File> resolveFullModulePath() {
        final var map = new HashMap<String, Set<File>>();

        final Set<File> xdkContents = resolveDirectories(xdkContentsDirAtConfigurationTime);
        map.put(XDK_CONFIG_NAME_CONTENTS, xdkContents);

        final Set<File> xtcModuleDeclarations = resolveFiles(getXtcModuleDependencies());
        map.put(XTC_CONFIG_NAME_MODULE_DEPENDENCY, xtcModuleDeclarations);

        for (final var entry : sourceSetOutputDirsAtConfigurationTime.entrySet()) {
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
        return xdkContentsDirAtConfigurationTime;
    }

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    FileCollection getXtcModuleDependencies() {
        return xtcModuleDependenciesAtConfigurationTime.get();
    }

    @Internal
    protected List<SourceSet> getDependentSourceSets() {
        // This method still returns SourceSet objects for compatibility, but they are reconstructed from names
        // TODO: Eventually replace this with a method that returns source set names only
        throw new UnsupportedOperationException("getDependentSourceSets() should not be called at execution time for configuration cache compatibility. Use sourceSetNamesAtConfigurationTime instead.");
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

    @SuppressWarnings("unused")
    protected Set<File> resolveFiles(final Provider<@NotNull Directory> dirProvider) {
        return resolveFiles(objects.fileCollection().from(dirProvider));
    }

    protected Set<File> resolveDirectories(final Provider<@NotNull Directory> dirProvider) {
        return resolveDirectories(resolveFiles(objects.fileCollection().from(dirProvider)));
    }

    private static char yesOrNo(final boolean value) {
        return value ? 'y' : 'n';
    }

    protected final List<String> resolveJvmArgs() {
        final var list = new ArrayList<>(getJvmArgs().get());
        if (getDebug().get()) {
            list.add(String.format("-Xrunjdwp:transport=dt_socket,server=y,suspend=%c,address=%d", yesOrNo(getDebugSuspend().get()), getDebugPort().get()));
            logger.lifecycle("[plugin] Added debug argument: {}", jvmArgs.get());
        }
        return Collections.unmodifiableList(list);
    }

}
