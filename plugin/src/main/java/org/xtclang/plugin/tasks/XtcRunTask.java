package org.xtclang.plugin.tasks;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
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
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecResult;
import org.xtclang.plugin.XtcBuildException;
import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.XtcRunModule;
import org.xtclang.plugin.XtcRuntimeExtension;
import org.xtclang.plugin.internal.DefaultXtcRunModule;
import org.xtclang.plugin.internal.DefaultXtcRuntimeExtension;
import org.xtclang.plugin.launchers.CommandLine;
import org.xtclang.plugin.launchers.XtcLauncher;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Collections.emptySet;
import static org.xtclang.plugin.XtcPluginConstants.XTC_RUNNER_CLASS_NAME;
import static org.xtclang.plugin.XtcProjectDelegate.incomingXtcModuleDependencies;

/**
 * Task that runs and XTC module, given at least its name, using the module path from
 * the XTC environment.
 * <p>
 * If the 'runXtc' task is called without any module specifications, it will try to run every module compiled
 * from the source set in undefined order. That would be the exact equivalent of calling 'runAllXtc'
 * (which does not need to be configured, and is a task added by the plugin).
 *
 * @see XtcRunAllTask
 */

// TODO: Add modules {} segment to runtime DSL
// TODO: Add a generic xtcPlugin or xtc extension, where we can set stuff like, e.g. log level for the plugin (which does not redirect)
// TODO: Add WorkerExecutor and the Gradle Worker API to execute in parallel if there are no dependencies.
//   Any task with zero defined outputs is not cacheable, which should be enough for all run tasks.
// TODO: @CachableTask in any form?
// The launcher task should implement all its accessors, not the run task.
public class XtcRunTask extends XtcLauncherTask<XtcRuntimeExtension> implements XtcRuntimeExtension {
    private final Map<DefaultXtcRunModule, ExecResult> executedModules; // TODO we can cache output here to if we want.

    // Inherited from extension convention
    private final Property<Boolean> showVersion;

    /**
     * Create an XTC run task, currently delegating instead of inheriting the plugin project
     * delegate. We are slowly getting rid of this delegate pattern, now that the intra-plugin
     * needed types have been resolved.
     *
     * @param delegate Project delegate
     * @param sourceSet Source set for the code of the module to be executed.
     */
    @Inject
    public XtcRunTask(final XtcProjectDelegate delegate, final SourceSet sourceSet) {
        super(delegate, sourceSet, delegate.xtcRuntimeExtension());
        var ext = delegate.xtcRuntimeExtension();
        this.executedModules = new LinkedHashMap<>();
        this.showVersion = objects.property(Boolean.class).convention(ext.getShowVersion());
        // Currently we just inherit modules from the run spec, we can change then in the run task later.
        // this.modules = objects.listProperty(XtcRunModule.class).convention(getExtension().getModules());
    }

    @Override
    protected XtcLauncher<XtcRuntimeExtension, XtcRunTask> createLauncher() {
        return XtcLauncher.create(delegate.getProject(), XTC_RUNNER_CLASS_NAME, this);
    }

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    FileCollection getInputDeclaredDependencyModules() {
        return delegate.filesFrom(incomingXtcModuleDependencies(sourceSet)); // xtcModule and xtcModuleTest dependencies declared in the project dependency { scope section
    }

    @Optional // we may have a build that only relies on modules build by other projects, or other dependencies.
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    FileCollection getInputModulesCompiledByProject() {
        return delegate.getXtcCompilerOutputModules(sourceSet); // The output of the XTC compiler for this project and source set.
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.ABSOLUTE)
    Provider<Directory> getInputXdkModules() {
        return delegate.getXdkContentsDir(); // Modules in the XDK directory, if one exists.
    }

    @Input
    public Property<Boolean> getShowVersion() {
        return showVersion;
    }

    @Input
    @Override
    public ListProperty<XtcRunModule> getModules() {
        return getExtension().getModules();
    }

    private XtcBuildException extensionOnly(final String operation) {
        return delegate.buildException("Operation '{}' only available through xtcRun extension DSL at the moment.", operation);
    }

    @Override
    public XtcRunModule module(final Action<XtcRunModule> action) {
        throw extensionOnly("module");
    }

    @Override
    public void moduleName(final String name) {
        throw extensionOnly("moduleName");
    }

    @Override
    public void moduleNames(final String... modules) {
        throw extensionOnly("moduleNames");
    }

    @Override
    public void setModules(final List<XtcRunModule> modules) {
        throw extensionOnly("setModules");
    }

    @Override
    public void setModules(final XtcRunModule... modules) {
        throw extensionOnly("setModules");
    }

    @Override
    public void setModuleNames(final List<String> moduleNames) {
        throw extensionOnly("setModuleNames");
    }

    @Override
    public void setModuleNames(final String... moduleNames) {
        throw extensionOnly("setModuleNames");
    }

    @Internal
    @Override
    public boolean isEmpty() {
        return getExtension().isEmpty();
    }

    @TaskAction
    public void run() {
        start();
        final var cmd = new CommandLine(XTC_RUNNER_CLASS_NAME, getJvmArgs().get());
        cmd.addBoolean("-version", getShowVersion().get());
        cmd.addBoolean("-verbose", getIsVerbose().get());
        cmd.addRepeated("-L", delegate.resolveModulePath(name, getInputDeclaredDependencyModules()));
        final var launcher = createLauncher();
        moduleRunQueue().forEach(module -> runOne(module, launcher, cmd.copy()));
        logFinishedRuns();
    }

    private void logFinishedRuns() {
        checkResolvable();
        final int count = executedModules.size();
        delegate.lifecycle("{} '{}' Task executed {} modules:", prefix, name, count);
        int i = 0;
        for (final var entry : executedModules.entrySet()) {
            final DefaultXtcRunModule config = entry.getKey();
            final ExecResult result = entry.getValue();
            final String index = String.format("(%2d/%2d)", ++i, count);
            final boolean success = result.getExitValue() == 0;
            final LogLevel level = success ? LogLevel.LIFECYCLE : LogLevel.ERROR;
            delegate.log(level, "{} '{}' {}   {} {}", prefix, name, index, config.getModuleName().get(), config.toString());
            delegate.log(level, "{} '{}' {}       {} {}", prefix, name, index, success ? "SUCCESS" : "FAILURE", result);
        }
    }

    protected Stream<XtcRunModule> moduleRunQueue() {
        final var modulesToRun = resolveModulesToRun();
        delegate.lifecycle("{} '{}' Queued up {} module(s) to execute:", prefix, name, modulesToRun.size());
        // TODO: Allow parallel execution
        return modulesToRun.stream();
    }

    /**
     * Check if there are module { ... } declarations without names. TODO: Can use mandatory flag
     * NOTE: This function expects that the configuration phase is finished and everything resolves.
     */
    private List<XtcRunModule> validatedModules() {
        return getModules().get().stream().filter(m -> {
            if (!m.validate()) {
                logger.error("{} ERROR: XTC run module without module name was declared: {}", prefix, m);
                throw new GradleException(prefix + " Invalid module configuration: " + m);
            }
            return true;
        }).toList();
    }

    protected Collection<XtcRunModule> resolveModulesToRun() {
        // Given the module definition in the xtcRun closure in the DSL, create their equivalent POJOs.
        if (getExtension().isEmpty()) {
            // 1) No modules were declared
            delegate.warn("{} '{}' Configuration does not contain specified modules to run. Will default to 'xtcRunAll' task.", prefix, name);

            // 2) Examine all compiled modules for this project.
            final var allModules = resolveCompiledModules();
            if (allModules.isEmpty()) {
                // 3) We have no compiled modules for the project, return an empty execution queue.
                delegate.warn("{} '{}' There is nothing in the module path to run. Aborting.", prefix, name);
                return emptySet();
            }

            // 4) We do have modules compiled for this project. Add them all to the execution queue.
            allModules.forEach(m -> delegate.lifecycle("{} '{}'    Module '{}' added to execution queue.", prefix, name, m));
            return allModules;
        }

        return validatedModules();
    }

    private Collection<File> resolveCompiledModuleFiles() {
        final var allFiles = getInputModulesCompiledByProject().getAsFileTree().getFiles();
        delegate.info("{} All XTC modules compiled for SourceSet '{}':", prefix, sourceSet.getName());
        allFiles.forEach(f -> delegate.info("{}    {}", prefix, f.getAbsolutePath()));
        return allFiles;
    }

    private Collection<XtcRunModule> resolveCompiledModules() {
        final var moduleFiles = resolveCompiledModuleFiles();
        final var allModules = moduleFiles.stream().map(File::getAbsolutePath).map(this::createModuleNamed).toList();
        delegate.lifecycle("{} '{}' Resolved {} module names or module file paths to run.", prefix, getName(), allModules.size());
        return allModules;
    }

    private XtcRunModule createModuleNamed(final String name) {
        return DefaultXtcRuntimeExtension.createModule(delegate.getProject(), name);
    }

    @SuppressWarnings("UnusedReturnValue")
    private ExecResult runOne(final XtcRunModule runConfig, final XtcLauncher<XtcRuntimeExtension, XtcRunTask> launcher, final CommandLine cmd) {
        delegate.info("{} Executing resolved xtcRuntime module closure: {}", prefix, runConfig);
        final var moduleMethod = runConfig.getMethodName().get();
        if (!runConfig.hasDefaultMethodName()) {
            cmd.add("-M", moduleMethod);
        }
        final var moduleName = runConfig.getModuleName().get();
        cmd.addRaw(moduleName);
        cmd.addRaw(runConfig.getModuleArgs().get());
        //cmd.addRaw(runConfig.resolveModuleArgs());
        final ExecResult result = launcher.apply(cmd);
        executedModules.put((DefaultXtcRunModule)runConfig, result);
        delegate.info("{} '{}'    Finished executing: {}", prefix, name, moduleName);

        return handleExecResult(delegate.getProject(), result);
    }
}
