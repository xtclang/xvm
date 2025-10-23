package org.xtclang.plugin.tasks;

import static java.util.Collections.emptyList;

import static org.gradle.api.logging.LogLevel.ERROR;
import static org.gradle.api.logging.LogLevel.INFO;
import static org.gradle.api.logging.LogLevel.LIFECYCLE;

import static org.xtclang.plugin.XtcPluginConstants.XTC_RUNNER_CLASS_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_RUNNER_LAUNCHER_NAME;

import java.io.File;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
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
import org.gradle.process.ExecResult;

import org.jetbrains.annotations.NotNull;

import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.XtcRunModule;
import org.xtclang.plugin.XtcRuntimeExtension;
import org.xtclang.plugin.internal.DefaultXtcRuntimeExtension;
import org.xtclang.plugin.launchers.CommandLine;
import org.xtclang.plugin.launchers.DetachedJavaExecLauncher;
import org.xtclang.plugin.launchers.DetachedNativeBinaryLauncher;
import org.xtclang.plugin.launchers.XtcLauncher;

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
    private static final String XEC_ARG_RUN_METHOD = "--method";

    private final Map<XtcRunModule, ExecResult> executedModules; // TODO we can cache output here to if we want.
    private final Property<@NotNull DefaultXtcRuntimeExtension> taskLocalModules;

    // Captured at configuration time for configuration cache compatibility
    private final DirectoryProperty buildDirectory;
    private final DirectoryProperty projectDirectory;

    /**
     * Create an XTC run task, currently delegating instead of inheriting the plugin project
     * delegate. We are slowly getting rid of this delegate pattern, now that the intra-plugin
     * needed types have been resolved.
     *
     * @param project  Project
     */
    @SuppressWarnings("ConstructorNotProtectedInAbstractClass") // Has to be public for code injection to work.
    @Inject
    public XtcRunTask(final Project project) {
        // TODO clean this up:
        super(project, XtcProjectDelegate.resolveXtcRuntimeExtension(project));
        this.executedModules = new LinkedHashMap<>();
        this.taskLocalModules = objects.property(DefaultXtcRuntimeExtension.class).convention(objects.newInstance(DefaultXtcRuntimeExtension.class, project));

        // Capture directories at configuration time for configuration cache compatibility
        this.buildDirectory = objects.directoryProperty();
        this.buildDirectory.set(project.getLayout().getBuildDirectory());
        this.projectDirectory = objects.directoryProperty();
        this.projectDirectory.set(project.getLayout().getProjectDirectory());
    }


    @Internal
    @Override
    public final String getNativeLauncherCommandName() {
        return XTC_RUNNER_LAUNCHER_NAME;
    }

    @Internal
    @Override
    public final String getJavaLauncherClassName() {
        return XTC_RUNNER_CLASS_NAME;
    }

    @Override
    protected XtcLauncher<XtcRuntimeExtension, ? extends XtcLauncherTask<XtcRuntimeExtension>> createLauncher() {
        // Use parent's createLauncher for normal mode
        if (!getDetach().get()) {
            return super.createLauncher();
        }

        // Validate detach mode requirements - fail fast
        if (!useNativeLauncherValue && !forkValue) {
            throw new GradleException("[plugin] Detach mode requires fork=true. Set 'fork = true' in xtcRun configuration.");
        }

        // Extract common directory resolution for detached launchers (DRY)
        final File buildDir = buildDirectory.get().getAsFile();
        final File projectDir = projectDirectory.get().getAsFile();

        if (useNativeLauncherValue) {
            getLogger().info("[plugin] Created XTC launcher: detached native executable.");
            return new DetachedNativeBinaryLauncher<>(this, getLogger(), getExecOperations(), buildDir, projectDir);
        }

        // Must be fork mode (validated above)
        getLogger().info("[plugin] Created XTC launcher: detached Java process.");
        return new DetachedJavaExecLauncher<>(this, getLogger(), getExecOperations(),
            toolchainExecutable, projectVersion, xdkFileTree, javaToolsConfig, buildDir, projectDir);
    }

    // XTC modules needed to resolve module path (the contents of the XDK required to build and run this project)
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    Provider<@NotNull Directory> getInputXdkModules() {
        return xdkContentsDir;
    }

    // XTC modules needed to resolve module path (the ones in the output of the project source set, that the compileXtc tasks create)
    @Optional
    @InputFiles // should really be enough with an "input directories" but that doesn't exist in gradle.
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
    @Input
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
    public Property<@NotNull Boolean> getDetach() {
        return getExtension().getDetach();
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

    // TODO: Have the task depend on actual output of all source sets.
    @TaskAction
    @Override
    public void executeTask() {
        super.executeTask();

        // Validate that parallel execution is not enabled (not yet implemented)
        if (getParallel().get()) {
            throw new UnsupportedOperationException("""
                [plugin] Parallel module execution is not yet implemented. \
                Please set 'parallel = false' in your xtcRun configuration or remove the parallel setting.\
                """);
        }

        final var cmd = new CommandLine(XTC_RUNNER_CLASS_NAME, resolveJvmArgs());
        cmd.addBoolean("--version", getShowVersion().get());
        cmd.addBoolean("--verbose", getVerbose().get());
        // When using the Gradle XTC plugin, having the 'xec' runtime decide to recompile stuff, is not supposed to be a thing.
        // The whole point about the plugin is that we guarantee source->module up-to-date relationships, as long as you follow
        // the standard build lifecycle model.
        cmd.addBoolean("--no-recompile", true);

        // Create module path from xtcModule dependencies, XDK contents and output of our source set.
        final var modulePath = resolveFullModulePath();
        cmd.addRepeated("-L", modulePath);

        // Now we filter out only modules we have been specifically told to run.
        //
        //   1. If there are no explicit modules specified anywhere, we warn, and this task becomes a no-op.
        //   2. If there is a command line override, this means ONLY the one given on the command line (should still be on the module path)
        //   3. Otherwise, filter out the modules specified in the xtcRun extension or task DSL (should still be on the module path)
        //
        // This works similarly in an xtcRunAll task, only, the "filter", is to run all modules from the source set output.
        // (TODO: one might argue that it should be all runnable modules on the module path, but, let's argue about that later)
        final var modulesToRun = resolveModulesToRunFromModulePath(modulePath);
        final var results = modulesToRun.stream().map(module -> runSingleModule(module, createLauncher(), cmd.copy())).toList();
        if (modulesToRun.size() != results.size()) {
            logger.warn("[plugin] Task was configured to run {} modules, but only {} where executed.", modulesToRun.size(), results.size());
        }
        logFinishedRuns();
    }

    protected List<XtcRunModule> resolveModulesToRunFromModulePath(final List<File> resolvedModulePath) {
        checkIsBeingExecuted();

        logger.info("[plugin] Resolving modules to run from module path: '{}'", resolvedModulePath);
        if (isEmpty()) {
            logger.warn("[plugin] Task extension '{}' and/or local task configuration do not declare any modules to run for '{}'. Skipping task.", ext.getName(), getName());
            return emptyList();
        }

        final List<XtcRunModule> selectedModules = getModules().get();
        if (!taskLocalModules.get().isEmpty()) {
            logger.lifecycle("[plugin] Task local module configuration is present, overriding extension configuration.");
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
    private ExecResult runSingleModule(
        final XtcRunModule runConfig,
        final XtcLauncher<XtcRuntimeExtension,
        ? extends XtcLauncherTask<XtcRuntimeExtension>> launcher,
        final CommandLine cmd) {
        // TODO: Maybe make this inheritable + add a runMultipleModules, so that we can customize even better
        //  (e.g. XUnit, and a less hacky way of executing the XTC parallel test runner, for example)
        logger.info("[plugin] Executing resolved xtcRuntime module closure: {}", runConfig);
        final var moduleMethod = runConfig.getMethodName().get();
        if (!runConfig.hasDefaultMethodName()) {
            cmd.add(XEC_ARG_RUN_METHOD, moduleMethod);
        }

        final var moduleName = runConfig.getModuleName().get();
        cmd.addRaw(moduleName);
        cmd.addRaw(runConfig.getModuleArgs().get());

        final ExecResult result = launcher.apply(cmd);
        executedModules.put(runConfig, result);
        logger.info("[plugin]    Finished executing: {}", moduleName);

        return handleExecResult(result);
    }

    private void logFinishedRuns() {
        checkIsBeingExecuted();
        final int count = executedModules.size();
        logger.info("[plugin] Task executed {} modules:", count);
        int i = 0;
        for (final var entry : executedModules.entrySet()) {
            final XtcRunModule config = entry.getKey();
            final ExecResult result = entry.getValue();
            final String index = String.format("(%2d/%2d)", ++i, count);
            final boolean success = result.getExitValue() == 0;
            final LogLevel level = success ? (hasVerboseLogging() ? LIFECYCLE : INFO) : ERROR;
            logger.log(level, "[plugin] {}   {} {}", index, config.getModuleName().get(), config.toString(true));
            logger.log(level, "[plugin] {}       {} {}", index, success ? "SUCCESS" : "FAILURE", result);
        }
    }

    @Override
    public String toString() {
        return projectName + ':' + getName() + " [class: " + getClass().getSimpleName() + ']';
    }
}
