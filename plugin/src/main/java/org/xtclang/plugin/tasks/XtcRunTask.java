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

import javax.inject.Inject;

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

import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.XtcRunModule;
import org.xtclang.plugin.XtcRuntimeExtension;
import org.xtclang.plugin.internal.DefaultXtcRuntimeExtension;
import org.xtclang.plugin.launchers.CommandLine;
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

    private Map<XtcRunModule, ExecResult> executedModules; // TODO we can cache output here to if we want.
    private Property<DefaultXtcRuntimeExtension> taskLocalModules;

    /**
     * Create an XTC run task, currently delegating instead of inheriting the plugin project
     * delegate. We are slowly getting rid of this delegate pattern, now that the intra-plugin
     * needed types have been resolved.
     */
    @SuppressWarnings({"ConstructorNotProtectedInAbstractClass", "this-escape"}) // Has to be public for code injection to work.
    @Inject
    public XtcRunTask() {
        // TODO clean this up:
        super(null); // Extension will be resolved lazily
        // Don't initialize fields here - let them be initialized lazily
    }
    
    private Property<DefaultXtcRuntimeExtension> getTaskLocalModules() {
        if (taskLocalModules == null) {
            taskLocalModules = getObjects().property(DefaultXtcRuntimeExtension.class);
            // Don't set convention here - it will be set lazily when needed
        }
        return taskLocalModules;
    }
    
    private Map<XtcRunModule, ExecResult> getExecutedModules() {
        if (executedModules == null) {
            executedModules = new LinkedHashMap<>();
        }
        return executedModules;
    }
    
    @Override
    protected XtcRuntimeExtension getExtension() {
        if (ext == null) {
            // Defensive check to ensure project is available
            if (getProject() != null) {
                ext = XtcProjectDelegate.resolveXtcRuntimeExtension(getProject());
            } else {
                throw new IllegalStateException("Project is not available during extension resolution");
            }
        }
        return ext;
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

    private Provider<Directory> inputXdkModules;
    private FileCollection inputModulesCompiledByProject;
    
    // XTC modules needed to resolve module path (the contents of the XDK required to build and run this project)
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    Provider<Directory> getInputXdkModules() {
        if (inputXdkModules == null) {
            try {
                inputXdkModules = getLayout().getBuildDirectory().dir("xtc/xdk/lib");
            } catch (Exception e) {
                // During task creation, layout might not be available yet
                // Return a dummy provider that will be resolved later
                inputXdkModules = getObjects().directoryProperty();
            }
        }
        return inputXdkModules;
    }

    // XTC modules needed to resolve module path (the ones in the output of the project source set, that the compileXtc tasks create)
    @Optional
    @InputFiles // should really be enough with an "inputdirectories" but that doesn't exist in gradle.
    @PathSensitive(PathSensitivity.RELATIVE)
    FileCollection getInputModulesCompiledByProject() {
        if (inputModulesCompiledByProject == null) {
            try {
                inputModulesCompiledByProject = getObjects().fileCollection().from(
                    getLayout().getBuildDirectory().dir("xtc")
                );
            } catch (Exception e) {
                // During task creation, layout might not be available yet
                inputModulesCompiledByProject = getObjects().fileCollection();
            }
        }
        return inputModulesCompiledByProject;
    }

    private ListProperty<XtcRunModule> modules;
    
    // TODO: We may need to keep track of all input, even though we only resolve one out of three possible run configurations.
    //   XTC Modules declared in run configurations in project, or overridden in task, that we want to run.
    @Input
    @Override
    public ListProperty<XtcRunModule> getModules() {
        if (modules == null) {
            modules = getObjects().listProperty(XtcRunModule.class);
            // Don't set convention here - it will be resolved during execution
        }
        return modules;
    }

    @Override
    public XtcRunModule module(final Action<XtcRunModule> action) {
        return getTaskLocalModules().get().module(action);
    }

    @Override
    public void moduleName(final String moduleName) {
        // Ensure task local modules property exists
        if (!getTaskLocalModules().isPresent()) {
            try {
                getTaskLocalModules().set(getObjects().newInstance(DefaultXtcRuntimeExtension.class, getProject()));
            } catch (Exception e) {
                // If we can't create it now, it will be created later
                return;
            }
        }
        getTaskLocalModules().get().moduleName(moduleName);
    }

    @Override
    public void moduleNames(final String... moduleNames) {
        // Ensure task local modules property exists
        if (!getTaskLocalModules().isPresent()) {
            try {
                getTaskLocalModules().set(getObjects().newInstance(DefaultXtcRuntimeExtension.class, getProject()));
            } catch (Exception e) {
                // If we can't create it now, it will be created later
                return;
            }
        }
        getTaskLocalModules().get().moduleNames(moduleNames);
    }

    @Override
    public void setModules(final List<XtcRunModule> modules) {
        // Ensure task local modules property exists
        if (!getTaskLocalModules().isPresent()) {
            try {
                getTaskLocalModules().set(getObjects().newInstance(DefaultXtcRuntimeExtension.class, getProject()));
            } catch (Exception e) {
                // If we can't create it now, it will be created later
                return;
            }
        }
        getTaskLocalModules().get().setModules(modules);
    }

    @Override
    public void setModules(final XtcRunModule... modules) {
        // Ensure task local modules property exists
        if (!getTaskLocalModules().isPresent()) {
            try {
                getTaskLocalModules().set(getObjects().newInstance(DefaultXtcRuntimeExtension.class, getProject()));
            } catch (Exception e) {
                // If we can't create it now, it will be created later
                return;
            }
        }
        getTaskLocalModules().get().setModules(modules);
    }

    @Override
    public void setModuleNames(final List<String> moduleNames) {
        // Ensure task local modules property exists
        if (!getTaskLocalModules().isPresent()) {
            try {
                getTaskLocalModules().set(getObjects().newInstance(DefaultXtcRuntimeExtension.class, getProject()));
            } catch (Exception e) {
                // If we can't create it now, it will be created later
                return;
            }
        }
        getTaskLocalModules().get().setModuleNames(moduleNames);
    }

    @Override
    public void setModuleNames(final String... moduleNames) {
        // Ensure task local modules property exists
        if (!getTaskLocalModules().isPresent()) {
            try {
                getTaskLocalModules().set(getObjects().newInstance(DefaultXtcRuntimeExtension.class, getProject()));
            } catch (Exception e) {
                // If we can't create it now, it will be created later
                return;
            }
        }
        getTaskLocalModules().get().setModuleNames(moduleNames);
    }

    @Internal
    @Override
    public boolean isEmpty() {
        boolean taskLocalEmpty = !getTaskLocalModules().isPresent() || getTaskLocalModules().get().isEmpty();
        return taskLocalEmpty && getExtension().isEmpty();
    }

    @Override
    public int size() {
        if (!getTaskLocalModules().isPresent() || getTaskLocalModules().get().isEmpty()) {
            return getExtension().size();
        }
        return getTaskLocalModules().get().size();
    }

    // TODO: Have the task depend on actual output of all source sets.
    @TaskAction
    @Override
    public void executeTask() {
        super.executeTask();

        final var cmd = new CommandLine(XTC_RUNNER_CLASS_NAME, resolveJvmArgs());
        cmd.addBoolean("--version", getShowVersion().get());
        cmd.addBoolean("--verbose", getVerbose().get());
        // When using the Gradle XTC plugin, having the 'xec' runtime decide to recompile stuff, is not supposed to be a thing.
        // The whole point about the plugin is that we guarantee source->module up-to-date relationships, as long as you follow
        // the standard build lifecycle model.
        cmd.addBoolean("--no-recompile", true);

        // Create module path from xtcModule dependenciex, XDK contents and output of our source set.
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
            getLogger().warn("{} Task was configured to run {} modules, but only {} where executed.", prefix(), modulesToRun.size(), results.size());
        }
        logFinishedRuns();
    }

    protected List<XtcRunModule> resolveModulesToRunFromModulePath(final List<File> resolvedModulePath) {
        checkIsBeingExecuted();

        getLogger().lifecycle("{} Resolving modules to run from module path: '{}'", prefix(), resolvedModulePath);
        final var prefix = prefix();

        if (isEmpty()) {
            getLogger().warn("{} Task extension '{}' and/or local task configuration do not declare any modules to run for '{}'. Skipping task.",
                prefix, ext.getName(), getName());
            return emptyList();
        }

        final List<XtcRunModule> selectedModules;
        if (getTaskLocalModules().isPresent() && !getTaskLocalModules().get().isEmpty()) {
            selectedModules = getTaskLocalModules().get().getModules().get();
            getLogger().lifecycle("{} Task local module configuration is present, overriding extension configuration.", prefix);
        } else {
            selectedModules = getExtension().getModules().get();
        }

        // TODO: Add abstraction that actually implements a ModulePath instance, including keeping track of its status and perhaps a method for resolving it.
        // TODO: Here we should check that any module we resolve is actually in the source set output of the source set of this task.
        //   Strictly speaking, of course, we could declare any module as long as it's on the module path. Let's discuss this later.

        // If we don't have any source set output, we should already return empty amd warn
        // If we do have a module spec with one or more modules, we will queue them up to run in sequence.
        // We will also check if the modules are in the module path, and if not, fail the build.
        getLogger().info("{} Found {} modules(s) in task and extension specification.", prefix, size());
        selectedModules.forEach(module -> getLogger().info("{}    ***** Module to run: {}", prefix, module));

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
    private ExecResult runSingleModule(
        final XtcRunModule runConfig,
        final XtcLauncher<XtcRuntimeExtension,
        ? extends XtcLauncherTask<XtcRuntimeExtension>> launcher,
        final CommandLine cmd) {
        // TODO: Maybe make this inheritable + add a runMultipleModules, so that we can customize even better
        //  (e.g. XUnit, and a less hacky way of executing the XTC parallel test runner, for example)
        getLogger().info("{} Executing resolved xtcRuntime module closure: {}", prefix(), runConfig);
        final var moduleMethod = runConfig.getMethodName().get();
        if (!runConfig.hasDefaultMethodName()) {
            cmd.add(XEC_ARG_RUN_METHOD, moduleMethod);
        }

        final var moduleName = runConfig.getModuleName().get();
        cmd.addRaw(moduleName);
        cmd.addRaw(runConfig.getModuleArgs().get());

        final ExecResult result = launcher.apply(cmd);
        getExecutedModules().put(runConfig, result);
        getLogger().info("{}    Finished executing: {}", prefix(), moduleName);

        return handleExecResult(result);
    }

    private void logFinishedRuns() {
        checkIsBeingExecuted();
        final var prefix = prefix();
        final int count = getExecutedModules().size();
        getLogger().info("{} Task executed {} modules:", prefix, count);
        int i = 0;
        for (final var entry : getExecutedModules().entrySet()) {
            final XtcRunModule config = entry.getKey();
            final ExecResult result = entry.getValue();
            final String index = String.format("(%2d/%2d)", ++i, count);
            final boolean success = result.getExitValue() == 0;
            final LogLevel level = success ? (hasVerboseLogging() ? LIFECYCLE : INFO) : ERROR;
            getLogger().log(level, "{} {}   {} {}", prefix, index, config.getModuleName().get(), config.toString(true));
            getLogger().log(level, "{} {}       {} {}", prefix, index, success ? "SUCCESS" : "FAILURE", result);
        }
    }

    @Override
    public String toString() {
        return getProject().getName() + ':' + getName() + " [class: " + getClass().getSimpleName() + ']';
    }
}
