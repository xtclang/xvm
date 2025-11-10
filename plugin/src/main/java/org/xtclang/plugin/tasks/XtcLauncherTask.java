package org.xtclang.plugin.tasks;

import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_JAVATOOLS_INCOMING;
import static org.xtclang.plugin.XtcPluginUtils.argumentArrayToList;

import java.io.File;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
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
import org.gradle.process.ExecResult;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcLauncherTaskExtension;
import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.internal.GradlePhaseAssertions;
import org.xtclang.plugin.launchers.JavaClasspathLauncher;
import org.xtclang.plugin.launchers.LauncherContext;
import org.xtclang.plugin.launchers.ModulePathResolver;
import org.xtclang.plugin.launchers.XtcLauncher;

/**
 * Abstract class that represents and XTC Launcher execution (i.e. Compiler, Runner, Disassembler etc.),
 * anything that goes through the XTC Launcher to spawn or call different processes
 */
public abstract class XtcLauncherTask<E extends XtcLauncherTaskExtension> extends XtcDefaultTask implements XtcLauncherTaskExtension {

    // All inherited from launcher task extension and turned into input
    final ConfigurableFileCollection modulePath;
    final Property<@NotNull String> stdoutPath;
    final Property<@NotNull String> stderrPath;
    final ListProperty<@NotNull String> jvmArgs;
    final Property<@NotNull Boolean> verbose;
    final Property<@NotNull Boolean> showVersion;

    final E ext;

    // Captured at configuration time for configuration cache compatibility
    final Provider<@NotNull Directory> projectDirectory;
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

    @SuppressWarnings("this-escape") // Suppressed because launchers need task reference in constructor
    protected XtcLauncherTask(final Project project, final E ext) {
        // Parent XtcDefaultTask initializes objects, providers, logger fields
        super();

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

        this.stdoutPath = objects.property(String.class);
        this.stderrPath = objects.property(String.class);

        if (ext.getStdoutPath().isPresent()) {
            stdoutPath.set(ext.getStdoutPath());
        }
        if (ext.getStderrPath().isPresent()) {
            stderrPath.set(ext.getStderrPath());
        }

        this.jvmArgs = objects.listProperty(String.class).convention(ext.getJvmArgs());

        this.verbose = objects.property(Boolean.class).convention(ext.getVerbose());
        this.showVersion = objects.property(Boolean.class).convention(ext.getShowVersion());

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

    //@Inject
    //public abstract ExecOperations getExecOperations();

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
        // Use objects factory instead of Project to create provider
        jvmArgs(getObjects().listProperty(String.class).value(arg.map(Collections::singletonList)));
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
     * Returns the source set names captured at configuration time.
     * This is an input because changes to source sets affect the module path.
     */
    @Input
    public List<String> getSourceSetNames() {
        return sourceSetNames;
    }

    @Internal
    public abstract String getJavaLauncherClassName();

    protected <R extends ExecResult> R handleExecResult(final R result) {
        final int exitValue = result.getExitValue();
        if (exitValue != 0) {
            final String taskName = getName();
            final String launcherType = "JavaClasspath (fork=" + ext.getFork().get() + ")";
            getLogger().error("""

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
        final var logger = getLogger();
        final boolean fork = ext.getFork().get();
        if (fork) {
            logger.lifecycle("[plugin] Using JavaClasspathLauncher with fork=true (separate process)");
        } else {
            logger.lifecycle("[plugin] Using JavaClasspathLauncher with fork=false (in-process execution)");
        }

        final var context = new LauncherContext(
            projectVersion,
            xdkFileTree,
            javaToolsConfig,
            toolchainExecutable,
            projectDirectory.get().getAsFile()
        );

        return new JavaClasspathLauncher<>(this, logger, context, fork);
    }

    protected List<File> resolveFullModulePath() {
        return new ModulePathResolver(this).resolveFullModulePath();
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
    public Provider<@NotNull FileCollection> getXtcModuleDependenciesProvider() {
        return xtcModuleDependencies;
    }

    @Internal
    public Map<String, Provider<@NotNull Directory>> getSourceSetOutputDirs() {
        return sourceSetOutputDirs;
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

    protected final List<String> resolveJvmArgs() {
        final var list = new ArrayList<>(getJvmArgs().get());
        // Debug arguments are now specified via jvmArgs() - use standard JDWP format:
        // jvmArgs("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
        return Collections.unmodifiableList(list);
    }

}
