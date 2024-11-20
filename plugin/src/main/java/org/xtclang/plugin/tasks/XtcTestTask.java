package org.xtclang.plugin.tasks;

import static org.xtclang.plugin.XtcPluginConstants.XTC_LAUNCHER_CLASS_NAME;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecResult;

import org.xtclang.plugin.XtcRunModule;
import org.xtclang.plugin.XtcRuntimeExtension;
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
public abstract class XtcTestTask extends XtcBaseRunTask implements XtcRuntimeExtension {
    /**
     * Create an XTC test task.
     *
     * @param project  Project
     */
    @SuppressWarnings("ConstructorNotProtectedInAbstractClass") // Has to be public for code injection to work.
    @Inject
    public XtcTestTask(final Project project) {
        super(project);
    }

    @Internal
    @Override
    public final String getJavaLauncherClassName() {
        return XTC_LAUNCHER_CLASS_NAME;
    }

    // TODO: Have the task depend on actual output of all source sets.
    @TaskAction
    @Override
    public void executeTask() {
        super.executeTask();

        final var cmd = new CommandLine(XTC_LAUNCHER_CLASS_NAME, resolveJvmArgs());
        cmd.addRaw("test");
        cmd.addBoolean("--version", getShowVersion().get());
        cmd.addBoolean("--verbose", getVerbose().get());
        // When using the Gradle XTC plugin, having the 'xtc' runtime decide to recompile stuff, is not supposed to be a thing.
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
        final var results = modulesToRun.stream().map(module -> testSingleModule(module, createLauncher(), cmd.copy())).toList();
        if (modulesToRun.size() != results.size()) {
            logger.warn("{} Task was configured to run {} modules, but only {} where executed.", prefix(), modulesToRun.size(), results.size());
        }
        logFinishedRuns();
    }


    private ExecResult testSingleModule(
        final XtcRunModule runConfig,
        final XtcLauncher<XtcRuntimeExtension, ? extends XtcLauncherTask<XtcRuntimeExtension>> launcher,
        final CommandLine cmd) {
        // TODO: Maybe make this inheritable + add a runMultipleModules, so that we can customize even better
        //  (e.g. XUnit, and a less hacky way of executing the XTC parallel test runner, for example)
        logger.info("{} Executing resolved xtcRuntime module closure: {}", prefix(), runConfig);

        final var moduleName = runConfig.getModuleName().get();
        cmd.addRaw(moduleName);
        cmd.addRaw(runConfig.getModuleArgs().get());

        final ExecResult result = launcher.apply(cmd);
        executedModules.put(runConfig, result);
        logger.info("{}    Finished executing: {}", prefix(), moduleName);

        return handleExecResult(result);
    }
}
