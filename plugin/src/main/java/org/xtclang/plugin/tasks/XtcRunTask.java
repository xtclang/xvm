package org.xtclang.plugin.tasks;

import static org.gradle.api.logging.LogLevel.ERROR;
import static org.gradle.api.logging.LogLevel.INFO;
import static org.gradle.api.logging.LogLevel.LIFECYCLE;

import static org.xtclang.plugin.XtcPluginConstants.XTC_RUNNER_CLASS_NAME;
import static org.xtclang.plugin.XtcPluginUtils.failure;

import java.io.File;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.LogLevel;
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
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.XtcRunModule;
import org.xtclang.plugin.XtcRuntimeExtension;
import org.xtclang.plugin.internal.DefaultXtcRunModule;
import org.xtclang.plugin.internal.DefaultXtcRuntimeExtension;
import org.xtclang.plugin.launchers.AttachedStrategy;
import org.xtclang.plugin.launchers.DetachedStrategy;
import org.xtclang.plugin.launchers.DirectStrategy;
import org.xtclang.plugin.launchers.ExecutionMode;
import org.xtclang.plugin.launchers.ExecutionStrategy;

/**
 * Task that runs and XTC module, given at least its name, using the module path from
 * the XTC environment.
 * <p>
 * We add a default run task to an XTC project. The default is either run nothing, or run everything.
 * First it looks in xtcRun and tries to run the modules from there.
 *    If no modules are there, we just say "nothing to run"
 *    Later - support command line modules, if we don't want to just keep that logic in build scripts, which is fine and can be lazy too.
 *    If there is a module command line, we replace the xtcRun modules with these, and we leave the tasks alone.
 *    They still override, but if you haven't touched the default xtcRun task that does what you want, i.e. runXtc -PmoduleName=foo
 * If there is a module config in the task, it overrides/replaces those totally.
 * <p>
 * We should easily be able to create XTC run tasks of our own
 * If the 'runXtc' task is called without any module specifications, it will try to run every module compiled
 * from the source set in undefined order. That would be the exact equivalent of calling 'runAllXtc'
 * (which does not need to be configured, and is a task added by the plugin).
 */

// TODO: Add modules {} segment to runtime DSL
// TODO: Add a generic xtcPlugin or xtc extension, where we can set stuff like, e.g. log level for the plugin (which does not redirect)
// TODO: Add WorkerExecutor and the Gradle Worker API to execute in parallel if there are no dependencies.
//   Any task with zero defined outputs is not cacheable, which should be enough for all run tasks.
// TODO: Make the module path/set pattern filterable for the module DSL.
public abstract class XtcRunTask extends XtcLauncherTask<XtcRuntimeExtension> implements XtcRuntimeExtension {
    protected final Map<XtcRunModule, Integer> executedModules; // Module -> exit code
    private final Property<@NotNull DefaultXtcRuntimeExtension> taskLocalModules;

    // Command-line override properties (set via --module, --method, --args options)
    private final Property<String> cliModuleName;
    private final Property<String> cliMethodName;
    private final ListProperty<String> cliModuleArgs;

    /**
     * Create an XTC run task, currently delegating instead of inheriting the plugin project
     * delegate. We are slowly getting rid of this delegate pattern, now that the intra-plugin
     * needed types have been resolved.
     *
     * @param project  Project
     */
    @SuppressWarnings({"ConstructorNotProtectedInAbstractClass", "this-escape"}) // Has to be public for code injection to work
    @Inject
    public XtcRunTask(final ObjectFactory objects, final Project project) {
        super(objects, project, XtcProjectDelegate.resolveXtcRuntimeExtension(project));
        this.executedModules = new LinkedHashMap<>();
        this.taskLocalModules = objects.property(DefaultXtcRuntimeExtension.class).convention(objects.newInstance(DefaultXtcRuntimeExtension.class));
        this.cliModuleName = objects.property(String.class);
        this.cliMethodName = objects.property(String.class);
        this.cliModuleArgs = objects.listProperty(String.class);
    }

    // =========================================================================
    // Command-line options (override build.gradle.kts configuration)
    // Usage: ./gradlew runXtc --module=MyModule --method=main --args=arg1,arg2
    // =========================================================================

    /**
     * Override the module to run from command line.
     * Example: ./gradlew runXtc --module=MyModule
     */
    @Option(option = "module", description = "Module name to run (overrides xtcRun configuration)")
    public void setCliModuleName(final String moduleName) {
        this.cliModuleName.set(moduleName);
    }

    @Internal
    public Property<String> getCliModuleName() {
        return cliModuleName;
    }

    /**
     * Override the method to invoke from command line.
     * Example: ./gradlew runXtc --method=main
     */
    @Option(option = "method", description = "Method name to invoke (default: run)")
    public void setCliMethodName(final String methodName) {
        this.cliMethodName.set(methodName);
    }

    @Internal
    public Property<String> getCliMethodName() {
        return cliMethodName;
    }

    /**
     * Override module arguments from command line (comma-separated).
     * Example: ./gradlew runXtc --args=hello,world
     */
    @Option(option = "args", description = "Module arguments (comma-separated)")
    public void setCliModuleArgs(final String args) {
        if (args != null && !args.isBlank()) {
            this.cliModuleArgs.set(List.of(args.split(",")));
        }
    }

    @Internal
    public ListProperty<String> getCliModuleArgs() {
        return cliModuleArgs;
    }


    @Internal
    @Override
    public String getJavaLauncherClassName() {
        return XTC_RUNNER_CLASS_NAME;
    }

    private ExecutionStrategy createStrategy() {
        final ExecutionMode mode = getExecutionMode().get();
        return switch (mode) {
            case DIRECT -> new DirectStrategy(logger);
            case ATTACHED -> new AttachedStrategy<>(logger, resolveJavaExecutable());
            case DETACHED -> new DetachedStrategy<>(logger, resolveJavaExecutable());
        };
    }

    private String resolveJavaExecutable() {
        final String executable = toolchainExecutable.getOrNull();
        if (executable == null) {
            throw new org.gradle.api.GradleException("Java toolchain not configured - cannot resolve java executable for forked execution");
        }
        return executable;
    }

    // XTC modules needed to resolve module path (the contents of the XDK required to build and run this project)
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    Provider<@NotNull Directory> getInputXdkModules() {
        return xdkContentsDir;
    }

    // XTC modules needed to resolve module path (the ones in the output of the project source set, that the compileXtc tasks create)
    @Optional
    @InputFiles // should really be enough with an "input directories" but that doesn't exist in Gradle.
    @PathSensitive(PathSensitivity.RELATIVE)
    FileCollection getInputModulesCompiledByProject() {
        final var result = objects.fileCollection();
        sourceSetNames.stream()
            .map(sourceSetOutputDirs::get)
            .filter(Objects::nonNull)
            .forEach(result::from);
        return result;
    }

    // TODO: We may need to keep track of all input, even though we only resolve one out of three possible run configurations.
    //   XTC Modules declared in run configurations in project, or overridden in task, that we want to run.
    // Note: @Input removed because this task is never up-to-date (see constructor where outputs are configured)
    // and the XtcRunModule objects are not serializable for configuration cache.
    @Internal
    @Override
    public ListProperty<@NotNull XtcRunModule> getModules() {
        if (taskLocalModules.get().isEmpty()) {
            return getExtension().getModules();
        }
        return taskLocalModules.get().getModules();
    }

    @Override
    public XtcRunModule module(final Action<@NotNull XtcRunModule> action) {
        return taskLocalModules.get().module(action);
    }

    @Override
    public void moduleName(final String moduleName) {
        taskLocalModules.get().moduleName(moduleName);
    }

    @Override
    public void moduleNames(final String... moduleNames) {
        taskLocalModules.get().moduleNames(moduleNames);
    }

    @Override
    public void setModules(final List<XtcRunModule> modules) {
        taskLocalModules.get().setModules(modules);
    }

    @Override
    public void setModules(final XtcRunModule... modules) {
        taskLocalModules.get().setModules(modules);
    }

    @Override
    public void setModuleNames(final List<String> moduleNames) {
        taskLocalModules.get().setModuleNames(moduleNames);
    }

    @Override
    public void setModuleNames(final String... moduleNames) {
        taskLocalModules.get().setModuleNames(moduleNames);
    }

    @Input
    @Override
    public Property<@NotNull Boolean> getParallel() {
        return getExtension().getParallel();
    }

    @Internal
    @Override
    public boolean isEmpty() {
        return taskLocalModules.get().isEmpty() && getExtension().isEmpty();
    }

    @Override
    public int size() {
        if (taskLocalModules.get().isEmpty()) {
            return getExtension().size();
        }
        return taskLocalModules.get().size();
    }

    @Internal
    public Property<@NotNull String> getMethodName() {
        // Return property with default "run" - method name is per-module, accessed via XtcRunModule
        return objects.property(String.class).convention("run");
    }

    // TODO: Have the task depend on actual output of all source sets.
    @TaskAction
    @Override
    public void executeTask() {
        super.executeTask();

        // Validate that parallel execution is not enabled (not yet implemented)
        if (getParallel().get()) {
            throw new UnsupportedOperationException("[plugin] Parallel module execution is not yet implemented. Please set 'parallel = false' in your xtcRun configuration or remove the parallel setting-");
        }

        // Create module path from xtcModule dependencies, XDK contents and output of our source set.
        final var modulePath = resolveFullModulePath();

        // Now we filter out only modules we have been specifically told to run.
        //
        //   1. If there are no explicit modules specified anywhere, we warn, and this task becomes a no-op.
        //   2. If there is a command line override, this means ONLY the one given on the command line (should still be on the module path)
        //   3. Otherwise, filter out the modules specified in the xtcRun extension or task DSL (should still be on the module path)
        //
        // This works similarly in an xtcRunAll task, only, the "filter", is to run all modules from the source set output.
        // (TODO: one might argue that it should be all runnable modules on the module path, but, let's argue about that later)
        final var modulesToRun = resolveModulesToRunFromModulePath(modulePath);
        final var strategy = createStrategy();
        final var results = modulesToRun.stream().map(module -> runSingleModule(module, strategy)).toList();
        if (modulesToRun.size() != results.size()) {
            logger.warn("[plugin] Task was configured to run {} modules, but only {} where executed.", modulesToRun.size(), results.size());
        }
        logFinishedRuns();
    }

    protected List<XtcRunModule> resolveModulesToRunFromModulePath(final List<File> resolvedModulePath) {
        logger.info("[plugin] Resolving modules to run from module path: '{}'", resolvedModulePath);

        // Check for command-line override: --module=MyModule takes precedence over all configuration
        if (cliModuleName.isPresent()) {
            final String moduleName = cliModuleName.get();
            logger.info("[plugin] Command-line override: running module '{}' (ignoring xtcRun configuration)", moduleName);
            final XtcRunModule cliModule = createCliOverrideModule(moduleName);
            return List.of(validatedModule(cliModule));
        }

        if (isEmpty()) {
            logger.info("[plugin] Task extension '{}' and/or local task configuration do not declare any modules to run for '{}'. Skipping task.", ext.getName(), getName());
            return List.of();
        }

        final var selectedModules = getModules().get();
        if (!taskLocalModules.get().isEmpty()) {
            logger.info("[plugin] Task local module configuration is present, overriding extension configuration.");
        }

        // TODO: Add abstraction that actually implements a ModulePath instance, including keeping track of its status and perhaps a method for resolving it.
        // TODO: Here we should check that any module we resolve is actually in the source set output of the source set of this task.
        //   Strictly speaking, of course, we could declare any module as long as it's on the module path. Let's discuss this later.

        // If we don't have any source set output, we should already return empty amd warn
        // If we do have a module spec with one or more modules, we will queue them up to run in sequence.
        // We will also check if the modules are in the module path, and if not, fail the build.
        logger.info("[plugin] Found {} modules(s) in task and extension specification.", size());
        selectedModules.forEach(module -> logger.info("[plugin]    ***** Module to run: {}", module));
        return selectedModules.stream().map(XtcRunTask::validatedModule).sorted().toList();
    }

    /**
     * Create an XtcRunModule from command-line options.
     */
    private XtcRunModule createCliOverrideModule(final String moduleName) {
        final var module = objects.newInstance(DefaultXtcRunModule.class);
        module.getModuleName().set(moduleName);
        if (cliMethodName.isPresent()) {
            module.getMethodName().set(cliMethodName.get());
        }
        if (cliModuleArgs.isPresent() && !cliModuleArgs.get().isEmpty()) {
            module.getModuleArgs().set(cliModuleArgs.get());
        }
        return module;
    }

    /**
     * Check if there are module { ... } declarations without names. TODO: Can use mandatory flag
     * NOTE: This function expects that the configuration phase is finished and everything resolves.
     */
    private static XtcRunModule validatedModule(final XtcRunModule module) {
        if (!module.validate()) {
            throw new GradleException("[plugin] ERROR: XtcRunModule was declared without a valid moduleName property: " + module);
        }
        return module;
    }

    @SuppressWarnings("UnusedReturnValue")
    protected int runSingleModule(final XtcRunModule runConfig, final ExecutionStrategy strategy) {
        // TODO: Maybe make this inheritable + add a runMultipleModules, so that we can customize even better
        //  (e.g. XUnit, and a less hacky way of executing the XTC parallel test runner, for example)
        logger.info("[plugin] Executing resolved xtcRuntime module closure: {}", runConfig);
        final int exitCode = executeStrategy(runConfig, strategy);
        executedModules.put(runConfig, exitCode);
        logger.info("[plugin]    Finished executing: {}", runConfig.getModuleName().get());
        if (exitCode != 0) {
            throw failure("Module execution failed with exit code: {}", exitCode);
        }
        return exitCode;
    }

    protected int executeStrategy(final XtcRunModule runConfig, final ExecutionStrategy strategy) {
        return strategy.execute(this, runConfig);
    }

    private void logFinishedRuns() {
        final int count = executedModules.size();
        logger.info("[plugin] Task executed {} modules:", count);
        int i = 0;
        for (final var entry : executedModules.entrySet()) {
            final XtcRunModule config = entry.getKey();
            final int exitCode = entry.getValue();
            final String index = String.format("(%2d/%2d)", ++i, count);
            final boolean success = exitCode == 0;
            final LogLevel level = success ? (hasVerboseLogging() ? LIFECYCLE : INFO) : ERROR;
            logger.log(level, "[plugin] {}   {} {}", index, config.getModuleName().get(), config.toString(true));
            logger.log(level, "[plugin] {}       {} (exit code: {})", index, success ? "SUCCESS" : "FAILURE", exitCode);
        }
    }
}
