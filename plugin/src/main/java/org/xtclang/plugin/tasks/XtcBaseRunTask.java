package org.xtclang.plugin.tasks;

import static java.util.Collections.emptyList;

import static org.gradle.api.logging.LogLevel.ERROR;
import static org.gradle.api.logging.LogLevel.INFO;
import static org.gradle.api.logging.LogLevel.LIFECYCLE;

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
import org.gradle.process.ExecResult;

import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.XtcRunModule;
import org.xtclang.plugin.XtcRuntimeExtension;
import org.xtclang.plugin.internal.DefaultXtcRuntimeExtension;

/**
 * A base class for task that run an XTC module (e.g. "run" and "test"), given at least its name,
 * using the module path from the XTC environment.
 */

// TODO: Add modules {} segment to runtime DSL
// TODO: Add a generic xtcPlugin or xtc extension, where we can set stuff like, e.g. log level for the plugin (which does not redirect)
// TODO: Add WorkerExecutor and the Gradle Worker API to execute in parallel if there are no dependencies.
//   Any task with zero defined outputs is not cacheable, which should be enough for all run tasks.
// TODO: Make the module path/set pattern filterable for the module DSL.
public abstract class XtcBaseRunTask extends XtcLauncherTask<XtcRuntimeExtension> implements XtcRuntimeExtension {
    protected final Map<XtcRunModule, ExecResult> executedModules; // TODO we can cache output here to if we want.
    protected final Property<DefaultXtcRuntimeExtension> taskLocalModules;

    /**
     * Create an XTC run task, currently delegating instead of inheriting the plugin project
     * delegate. We are slowly getting rid of this delegate pattern, now that the intra-plugin
     * needed types have been resolved.
     *
     * @param project  Project
     */
    @SuppressWarnings("ConstructorNotProtectedInAbstractClass") // Has to be public for code injection to work.
    @Inject
    public XtcBaseRunTask(final Project project) {
        // TODO clean this up:
        super(project, XtcProjectDelegate.resolveXtcRuntimeExtension(project));
        this.executedModules = new LinkedHashMap<>();
        this.taskLocalModules = objects.property(DefaultXtcRuntimeExtension.class).convention(objects.newInstance(DefaultXtcRuntimeExtension.class, project));
    }

    @Internal
    @Override
    public final String getNativeLauncherCommandName() {
        return XTC_RUNNER_LAUNCHER_NAME;
    }

    // XTC modules needed to resolve module path (the contents of the XDK required to build and run this project)
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    Provider<Directory> getInputXdkModules() {
        return XtcProjectDelegate.getXdkContentsDir(project); // Modules in the XDK directory, if one exists.
    }

    // XTC modules needed to resolve module path (the ones in the output of the project source set, that the compileXtc tasks create)
    @Optional
    @InputFiles // should really be enough with an "inputdirectories" but that doesn't exist in gradle.
    @PathSensitive(PathSensitivity.RELATIVE)
    FileCollection getInputModulesCompiledByProject() {
        FileCollection fc = objects.fileCollection();
        for (final var sourceSet : getDependentSourceSets()) {
            fc = fc.plus(XtcProjectDelegate.getXtcSourceSetOutput(project, sourceSet));
        }
        return fc;
    }

    // TODO: We may need to keep track of all input, even though we only resolve one out of three possible run configurations.
    //   XTC Modules declared in run configurations in project, or overridden in task, that we want to run.
    @Input
    @Override
    public ListProperty<XtcRunModule> getModules() {
        if (taskLocalModules.get().isEmpty()) {
            return getExtension().getModules();
        }
        return taskLocalModules.get().getModules();
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

    protected List<XtcRunModule> resolveModulesToRunFromModulePath(final List<File> resolvedModulePath) {
        checkIsBeingExecuted();

        logger.lifecycle("{} Resolving modules to run from module path: '{}'", prefix(), resolvedModulePath);
        final var prefix = prefix();

        if (isEmpty()) {
            logger.warn("{} Task extension '{}' and/or local task configuration do not declare any modules to run for '{}'. Skipping task.",
                prefix, ext.getName(), getName());
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
        // If we do have a module spec with one or more modules, we will queue them up to run in sequence.
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
    protected void logFinishedRuns() {
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
