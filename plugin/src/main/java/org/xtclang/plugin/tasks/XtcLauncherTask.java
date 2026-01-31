package org.xtclang.plugin.tasks;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPluginExtension;
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
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.options.OptionValues;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.jetbrains.annotations.NotNull;
import org.xtclang.plugin.XtcJavaToolsRuntime;
import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.internal.GradlePhaseAssertions;
import org.xtclang.plugin.launchers.ExecutionMode;
import org.xtclang.plugin.launchers.ModulePathResolver;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;
import static org.xtclang.plugin.XtcJavaToolsRuntime.ensureJavaToolsInClasspath;
import static org.xtclang.plugin.XtcPluginConstants.PROPERTY_VERBOSE_LOGGING_OVERRIDE;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_JAVATOOLS_INCOMING;
import static org.xtclang.plugin.XtcPluginUtils.argumentArrayToList;
import static org.xtclang.plugin.internal.GradlePhaseAssertions.validateConfigurationTimeCapture;

/**
 * Abstract class that represents and XTC Launcher execution (i.e. Compiler, Runner, Disassembler etc.),
 * anything that goes through the XTC Launcher to spawn or call different processes
 */
public abstract class XtcLauncherTask<E extends XtcLauncherTaskExtension> extends XtcDefaultTask implements XtcLauncherTaskExtension {
    public static final int EXIT_CODE_ERROR = 1;

    // All inherited from launcher task extension and turned into input
    final ConfigurableFileCollection modulePath;
    final Property<@NotNull String> stdoutPath;
    final Property<@NotNull String> stderrPath;
    final ListProperty<@NotNull String> jvmArgs;
    final Property<@NotNull Boolean> verbose;
    final Property<@NotNull Boolean> showVersion;
    final Property<@NotNull ExecutionMode> executionMode;

    final E ext;

    // Captured at configuration time for configuration cache compatibility
    final Provider<@NotNull Directory> projectDirectory;
    final Provider<@NotNull Directory> buildDirectory;
    final Provider<@NotNull Directory> xdkContentsDir;
    final Provider<@NotNull FileTree> xdkFileTree;
    final Map<String, Provider<@NotNull Directory>> sourceSetOutputDirs;
    final List<String> sourceSetNames;
    final Provider<@NotNull FileCollection> javaToolsConfig;
    final Provider<@NotNull FileCollection> xtcModuleDependencies;

    // Resolved launcher configuration as Providers for lazy evaluation.
    // Using Provider ensures values are resolved at execution time, allowing configuration
    // via tasks.configureEach {} to work correctly. Configuration cache compatible.
    protected final Provider<@NotNull String> toolchainExecutable;
    protected final Provider<@NotNull String> projectVersion;

    // Captured at configuration time to support xtcPluginOverrideVerboseLogging property
    private final boolean overrideVerboseLogging;

    protected XtcLauncherTask(final ObjectFactory objects, final Project project, final E ext) {
        super(objects);

        // Assert that we're in configuration phase during task construction
        GradlePhaseAssertions.assertProjectAccessDuringConfiguration(project, "XtcLauncherTask construction");
        this.ext = ext;
        this.overrideVerboseLogging = Boolean.parseBoolean(String.valueOf(project.findProperty(PROPERTY_VERBOSE_LOGGING_OVERRIDE)));

        // Capture at configuration time
        this.projectDirectory = objects.directoryProperty().value(project.getLayout().getProjectDirectory());
        this.buildDirectory = objects.directoryProperty().value(project.getLayout().getBuildDirectory());
        this.xdkContentsDir = XtcProjectDelegate.getXdkContentsDir(project);
        this.xdkFileTree = xdkContentsDir.map(dir -> objects.fileTree().setDir(dir));

        final var sourceSets = XtcProjectDelegate.getSourceSets(project);
        this.sourceSetNames = sourceSets.stream().map(SourceSet::getName).toList();
        this.sourceSetOutputDirs = sourceSets.stream()
            .collect(Collectors.toMap(SourceSet::getName,
                sourceSet -> XtcProjectDelegate.getXtcSourceSetOutputDirectory(project, sourceSet)
            ));

        this.stdoutPath = objects.property(String.class);
        this.stderrPath = objects.property(String.class);
        if (ext.getStdoutPath().isPresent()) {
            stdoutPath.set(ext.getStdoutPath());
        }
        if (ext.getStderrPath().isPresent()) {
            stderrPath.set(ext.getStderrPath());
        }

        this.modulePath = objects.fileCollection().from(ext.getModulePath());

        this.verbose = objects.property(Boolean.class).convention(ext.getVerbose());
        this.showVersion = objects.property(Boolean.class).convention(ext.getShowVersion());
        this.jvmArgs = objects.listProperty(String.class).convention(ext.getJvmArgs());
        this.executionMode = objects.property(ExecutionMode.class).convention(ext.getExecutionMode());

        // Assert that required configurations exist - they should always be created by this plugin
        final var configurations = project.getConfigurations();
        assert configurations.findByName(XDK_CONFIG_NAME_JAVATOOLS_INCOMING) != null : 
            "Configuration '" + XDK_CONFIG_NAME_JAVATOOLS_INCOMING + "' must exist - it should be created by XTC plugin during project configuration";
        
        this.javaToolsConfig = project.provider(() -> objects.fileCollection().from(configurations.getByName(XDK_CONFIG_NAME_JAVATOOLS_INCOMING)));

        // Build XTC module dependencies from all source sets - assert they exist too
        this.xtcModuleDependencies = project.provider(() -> {
            final var result = objects.fileCollection();
            sourceSetNames.stream()
                .map(sourceSetName -> {
                    final String configName = XtcProjectDelegate.incomingXtcModuleDependencies(sourceSetName);
                    requireNonNull(configName, "Incoming configuration not found: " + configName + " (source set: " + sourceSetName + ")");
                    return configurations.getByName(configName);
                })
                .forEach(result::from);
            return result;
        });
        
        // Capture JavaExecLauncher configuration at configuration time
        this.toolchainExecutable = project.provider(() -> {
            final var exts = project.getExtensions();
            final var javaExtension = exts.findByType(JavaPluginExtension.class);
            if (javaExtension != null) {
                final var toolchains = exts.getByType(JavaToolchainService.class);
                final var launcher = toolchains.launcherFor(javaExtension.getToolchain());
                return launcher.get().getExecutablePath().toString();
            }
            return null;
        });
        this.projectVersion = project.provider(() -> project.getVersion().toString());
        
        // Validate configuration-time captures for configuration cache compatibility
        validateConfigurationTimeCapture(this.xdkContentsDir, "XDK contents directory");
        validateConfigurationTimeCapture(this.sourceSetNames, "source set names");
        validateConfigurationTimeCapture(this.sourceSetOutputDirs, "source set output directories");
    }

    /**
     * Hook called at start of launcher task execution.
     * Ensures phase assertions and javatools loading before task execution.
     */
    protected void executeTask() {
        // Assert that we're in execution phase during task execution
        GradlePhaseAssertions.assertExecutionPhase(this, "XtcLauncherTask execution");

        // Ensure javatools.jar is loaded into the plugin classloader before any task uses LauncherOptions types
        // This is critical for published plugin users who have XDK as a dependency
        ensureJavaToolsInClasspath(
                getProjectVersion(),
                getJavaToolsConfiguration(),
                getXdkFileTree(),
                logger
        );
    }

    /**
     * Check if verbose logging is enabled for launcher tasks.
     * Checks (in order):
     * <ol>
     *   <li>Gradle --info/--debug flags (logger level)</li>
     *   <li>Project property: xtcPluginOverrideVerboseLogging=true</li>
     *   <li>Task/extension configuration: verbose = true</li>
     * </ol>
     */
    public boolean hasVerboseLogging() {
        // Gradle log levels take precedence
        if (logger.isInfoEnabled() || logger.isDebugEnabled()) {
            return true;
        }
        // Check global override property (captured at configuration time)
        if (overrideVerboseLogging) {
            return true;
        }
        // Check task-specific verbose setting
        return verbose.get();
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

    public boolean hasStdoutRedirect() {
        return stdoutPath.isPresent();
    }

    public boolean hasStderrRedirect() {
        return stderrPath.isPresent();
    }

    @SuppressWarnings("unused")
    public boolean hasOutputRedirects() {
        return hasStdoutRedirect() || hasStderrRedirect();
    }

    @Input
    @Optional
    public Property<@NotNull String> getStdoutPath() {
        return stdoutPath;
    }

    @Input
    @Optional
    public Property<@NotNull String> getStderrPath() {
        return stderrPath;
    }

    @Override
    public void jvmArg(final Provider<? extends @NotNull String> arg) {
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

    /**
     * Enable verbose output from command line.
     * Example: ./gradlew runXtc --verbose
     */
    @SuppressWarnings("unused")
    @Option(option = "verbose", description = "Enable verbose output")
    public void setVerboseOption(final boolean verbose) {
        this.verbose.set(verbose);
    }

    @Input
    public Property<@NotNull Boolean> getShowVersion() {
        return showVersion;
    }

    /**
     * Show XTC version before execution from command line.
     * Example: ./gradlew runXtc --show-version
     */
    @Option(option = "show-version", description = "Show XTC version before execution")
    public void setShowVersionOption(final boolean showVersion) {
        this.showVersion.set(showVersion);
    }

    @Input
    @Override
    public Property<@NotNull ExecutionMode> getExecutionMode() {
        return executionMode;
    }

    /**
     * Set execution mode from command line.
     * Example: ./gradlew runXtc --mode=DETACHED
     */
    @Option(option = "mode", description = "Execution mode: DIRECT, ATTACHED, or DETACHED")
    public void setExecutionModeOption(final ExecutionMode mode) {
        this.executionMode.set(mode);
    }

    /**
     * Lists available execution modes for command line help.
     */
    @SuppressWarnings("unused")
    @OptionValues("mode")
    public List<ExecutionMode> getAvailableExecutionModes() {
        return List.of(ExecutionMode.values());
    }

    @Optional
    @Input
    public ListProperty<@NotNull String> getJvmArgs() {
        return jvmArgs;
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
    public Provider<@NotNull Directory> getProjectDirectory() {
        return projectDirectory;
    }

    @Internal
    public Provider<@NotNull Directory> getBuildDirectory() {
        return buildDirectory;
    }

    public File resolveJavaTools() {
        // NOTE: To our utmost satisfaction we note that when building the XDK we bootstrap using the
        // "javatools-<semanticversion>.jar" artifact in the javatools outputs as javatools jar.
        // When we are running manualTests, which have an "xdk" dependency, we correctly grab it
        // from the distribution instead, e.g.: $HOME/src/xvm/manualTests/build/xtc/xdk/lib/javatools.jar
        return XtcJavaToolsRuntime.resolveJavaTools(
                getProjectVersion(),
                getJavaToolsConfiguration(),
                getXdkFileTree(),
                logger
        );
    }

    public List<File> resolveFullModulePath() {
        return new ModulePathResolver(
                logger,
                objects,
                xdkContentsDir,
                sourceSetOutputDirs,
                modulePath,
                xtcModuleDependencies
        ).resolveFullModulePath();
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public Provider<@NotNull Directory> getInputXdkContents() {
        return xdkContentsDir;
    }

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getXtcModuleDependencies() {
        return xtcModuleDependencies.get();
    }

    @Internal
    protected Provider<@NotNull String> getProjectVersion() {
        return projectVersion;
    }

    @Internal
    protected Provider<@NotNull FileCollection> getJavaToolsConfiguration() {
        return javaToolsConfig;
    }

    @Internal
    protected Provider<@NotNull FileTree> getXdkFileTree() {
        return xdkFileTree;
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
}
