package org.xtclang.plugin.tasks;

import org.gradle.api.Action;
import org.gradle.api.Project;
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
import org.gradle.process.ExecResult;
import org.xtclang.plugin.XtcBuildRuntimeException;
import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.XtcRunModule;
import org.xtclang.plugin.XtcRuntimeExtension;
import org.xtclang.plugin.internal.DefaultXtcRuntimeExtension;
import org.xtclang.plugin.launchers.CommandLine;
import org.xtclang.plugin.launchers.XtcLauncher;

import javax.inject.Inject;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.emptyList;
import static org.gradle.api.logging.LogLevel.ERROR;
import static org.gradle.api.logging.LogLevel.INFO;
import static org.gradle.api.logging.LogLevel.LIFECYCLE;
import static org.xtclang.plugin.XtcPluginConstants.XTC_RUNNER_CLASS_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_RUNNER_LAUNCHER_NAME;

/**
 * Task that runs and XTC module, given at least its name, using the module path from
 * the XTC environment.
 * <p>
 * We add a default run task to an XTC project. It's default is either run nothing, or run everything.
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
    private final Property<DefaultXtcRuntimeExtension> taskLocalModules;

    /**
     * Create an XTC run task, currently delegating instead of inheriting the plugin project
     * delegate. We are slowly getting rid of this delegate pattern, now that the intra-plugin
     * needed types have been resolved.
     *
     * @param project  Project
     */
    @Inject
    public XtcRunTask(final Project project) {
        // TODO clean this up:
        super(project, XtcProjectDelegate.resolveXtcRuntimeExtension(project)); //, XtcProjectDelegate.getSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME), XtcProjectDelegate.resolveXtcRuntimeExtension(project));
        this.executedModules = new LinkedHashMap<>();
        this.taskLocalModules = objects.property(DefaultXtcRuntimeExtension.class).convention(objects.newInstance(DefaultXtcRuntimeExtension.class, project));
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

    // XTC modules needed to resolve module path (the contents of the XDK required to build and run this project)
    @InputDirectory
    @PathSensitive(PathSensitivity.ABSOLUTE)
    Provider<Directory> getInputXdkModules() {
        return XtcProjectDelegate.getXdkContentsDir(project); // Modules in the XDK directory, if one exists.
    }

    // XTC modules needed to resolve module path (the ones in the output of the project source set, that the compileXtc tasks create)
    @Optional
    @InputFiles // should really be enough with an "inputdirectories" but that doesn't exist in gradle.
    @PathSensitive(PathSensitivity.ABSOLUTE)
    FileCollection getInputModulesCompiledByProject() {
        FileCollection fc = objects.fileCollection();
        for (final var sourceSet : getDependentSourceSets()) {
            fc = fc.plus(XtcProjectDelegate.getXtcSourceSetOutput(project, sourceSet));
        }
        return fc;
    }

    // TODO: We may need to keep track of all input, even though we only resolve one out of three possible run configurations.

    // XTC Modules declared in run configurations in project, or overridden in task, that we want to run. These should ALWAYS be a subset of getInputModulesCompiledByProject (TODO: Verify this)
    @Input
    @Override
    public ListProperty<XtcRunModule> getModules() {
        if (taskLocalModules.get().isEmpty()) {
            return getExtension().getModules();
        }
        return taskLocalModules.get().getModules();
    }

    private XtcBuildRuntimeException extensionOnly(final String operation) {
        return buildException("Operation '{}' only available through xtcRun extension DSL at the moment.", operation);
    }

    @Override
    public XtcRunModule module(final Action<XtcRunModule> action) {
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

        final var cmd = new CommandLine(XTC_RUNNER_CLASS_NAME, getJvmArgs().get());
        cmd.addBoolean("--version", getShowVersion().get());
        cmd.addBoolean("--verbose", getIsVerbose().get());
        // When using the Gradle XTC plugin, having the 'xec' runtime decide to recompile stuff, is not supposed to be a thing.
        // The whole point about the plugin is that we guarantee source->module up to date relationships, as long as you follow
        // the standard build lifecycle model.
        cmd.addBoolean("--no-recompile", true);

        // Create module path from xtcModule dependenciex, XDK contents and output of our source set.
        final var modulePath = resolveFullModulePath();
        cmd.addRepeated("-L", modulePath);

        // TODO:
        // This is ambiguous. For some reason, the Runner, when given library paths to the source set output,
        // declared dependencies, and the xdk, and then we run a module by name, even if it is on the module
        // path, it gets rebuilt, and placed in the output directory. The auto build tries to build the module
        // by finding its source in some sub set of the directories and it ends up directly under build, even
        // though it's refereshed. This also happens if touch and rebuild any x source file, and the compile
        // tasks works fine, but the runner somehow still decides that it's stale. It's really important that
        // the XTC runtimes do not create "invisible" dependency behavior that the plugin does not know about.

        //final var sourceSetOutput = XtcProjectDelegate.getXtcSourceSetOutputDirectory(project, XtcProjectDelegate.getMainSourceSet(project)).get().getAsFile();
        //logger.warn("{} WARNING: We hope the modules about to run are in '{}', because otherwise xec will assume they are stale and rebuild them in the wrong place.", prefix(), sourceSetOutput.getAbsolutePath());
        //cmd.add("-o", sourceSetOutput);

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
            logger.warn("{} Task was configured to run {} modules, but only {} where executed.", prefix(), modulesToRun.size(), results.size());
        }
        logFinishedRuns();
    }

    protected List<XtcRunModule> resolveModulesToRunFromModulePath(final List<File> resolvedModulePath) {
        checkIsBeingExecuted();

        logger.lifecycle("{} Resolving modules to run from module path: '{}'", prefix(), resolvedModulePath);
        final var prefix = prefix();

        if (isEmpty()) {
            logger.warn("{} Task extension '{}' and/or local task configuration do not declare any modules to run for '{}'. Skipping task.", prefix, ext.getName(), getName());
            return emptyList();
        }

        final List<XtcRunModule> selectedModules = getModules().get();
        if (!taskLocalModules.get().isEmpty()) {
            logger.lifecycle("{} Task local module configuration is present, overriding extension configuration.", prefix);
        }

        // TODO: Add abstraction that actually implements a ModulePath instance, including keeping track of its status and perhaps a method for resolving it.
        // TODO: Here we should check that any module we resolve is actually in the source set output of the source set of this task.
        //   Strictly speaking, of course, we could declare any module as long as it's on the module path. Let's discuss this later.

        // If we don't have any source set output, we should already return empty amd warn
        // If we do have a module spec with one or more modules, we will queue them up to run in sequnce.
        // We will also check if the modules are in the module path, and if not, fail the build.
        logger.info("{} Found {} modules(s) in task and extension specification.", prefix, size());
        selectedModules.forEach(module -> logger.info("{}    ***** Module to run: {}", prefix, module));

        return selectedModules.stream().map(this::validatedModule).sorted().toList();
    }

    /**
     * Check if there are module { ... } declarations without names. TODO: Can use mandatory flag
     * NOTE: This function expects that the configuration phase is finished and everything resolves.
     */
    private XtcRunModule validatedModule(final XtcRunModule module) {
        if (!module.validate()) {
            throw buildException("ERROR: XtcRunModule was declared without a valid moduleName property: {}", module);
        }
        return module;
    }

    @SuppressWarnings("UnusedReturnValue")
    private ExecResult runSingleModule(final XtcRunModule runConfig, final XtcLauncher<XtcRuntimeExtension, ? extends XtcLauncherTask<XtcRuntimeExtension>> launcher, final CommandLine cmd) {
        // TODO: Maybe make this inheritable + add a runMultipleModules, so that we can customize even better (e.g. XUnit, and a less hacky way of executing the XTC parallel test runner, for example)
        logger.info("{} Executing resolved xtcRuntime module closure: {}", prefix(), runConfig);
        final var moduleMethod = runConfig.getMethodName().get();
        if (!runConfig.hasDefaultMethodName()) {
            cmd.add(XEC_ARG_RUN_METHOD, moduleMethod);
        }

        final var moduleName = runConfig.getModuleName().get();
        cmd.addRaw(moduleName);
        cmd.addRaw(runConfig.getModuleArgs().get());

        final ExecResult result = launcher.apply(cmd);
        executedModules.put(runConfig, result);
        logger.info("{}    Finished executing: {}", prefix(), moduleName);

        return handleExecResult(result);
    }

    private void logFinishedRuns() {
        checkIsBeingExecuted();
        final var prefix = prefix();
        final int count = executedModules.size();
        logger.info("{} Task executed {} modules:", prefix, count);
        int i = 0;
        for (final var entry : executedModules.entrySet()) {
            final XtcRunModule config = entry.getKey();
            final ExecResult result = entry.getValue();
            final String index = String.format("(%2d/%2d)", ++i, count);
            final boolean success = result.getExitValue() == 0;
            final LogLevel level = success ? (hasVerboseLogging() ? LIFECYCLE : INFO) : ERROR;
            logger.log(level, "{} {}   {} {}", prefix, index, config.getModuleName().get(), config.toString(true));
            logger.log(level, "{} {}       {} {}", prefix, index, success ? "SUCCESS" : "FAILURE", result);
        }
    }

    @Override
    public String toString() {
        return projectName + ':' + getName() + " [class: " + getClass().getSimpleName() + ']';
    }
}
