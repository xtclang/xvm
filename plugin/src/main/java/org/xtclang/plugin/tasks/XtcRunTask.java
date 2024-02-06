package org.xtclang.plugin.tasks;

import org.gradle.api.Action;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.provider.ListProperty;
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
import org.xtclang.plugin.XtcBuildRuntimeException;
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
import static org.gradle.api.logging.LogLevel.ERROR;
import static org.gradle.api.logging.LogLevel.INFO;
import static org.gradle.api.logging.LogLevel.LIFECYCLE;
import static org.xtclang.plugin.XtcPluginConstants.XTC_RUNNER_CLASS_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_RUNNER_LAUNCHER_NAME;
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
// TODO: @CacheableTask in any form?
public abstract class XtcRunTask extends XtcLauncherTask<XtcRuntimeExtension> implements XtcRuntimeExtension {
    private final Map<DefaultXtcRunModule, ExecResult> executedModules; // TODO we can cache output here to if we want.

    /**
     * Create an XTC run task, currently delegating instead of inheriting the plugin project
     * delegate. We are slowly getting rid of this delegate pattern, now that the intra-plugin
     * needed types have been resolved.
     *
     * @param delegate Project delegate
     * @param taskName Name of this run task
     * @param sourceSet Source set for the code of the module to be executed.
     */
    @Inject
    public XtcRunTask(final XtcProjectDelegate delegate, final String taskName, final SourceSet sourceSet) {
        super(delegate, taskName, sourceSet, delegate.resolveXtcRuntimeExtension());
        this.executedModules = new LinkedHashMap<>();
        // TODO: Currently we just inherit modules from the run spec, we can change then in the run task later; e.g. // this.modules = objects.listProperty(XtcRunModule.class).convention(getExtension().getModules());
    }

    @SuppressWarnings("unused")
    @Internal
    public boolean isRunAllTask() {
        return false;
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
    @Override
    public ListProperty<XtcRunModule> getModules() {
        return getExtension().getModules();
    }

    private XtcBuildRuntimeException extensionOnly(final String operation) {
        return buildException("Operation '{}' only available through xtcRun extension DSL at the moment.", operation);
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
        cmd.addBoolean("--version", getShowVersion().get());
        cmd.addBoolean("--verbose", getIsVerbose().get());
        cmd.addRepeated("-L", resolveModulePath(getInputDeclaredDependencyModules()));
        final var launcher = createLauncher();
        moduleRunQueue().forEach(module -> runOne(module, launcher, cmd.copy()));
        logFinishedRuns();
    }

    private void logFinishedRuns() {
        checkResolvable();
        final int count = executedModules.size();
        logger.info("{} Task executed {} modules:", prefix, count);
        int i = 0;
        for (final var entry : executedModules.entrySet()) {
            final DefaultXtcRunModule config = entry.getKey();
            final ExecResult result = entry.getValue();
            final String index = String.format("(%2d/%2d)", ++i, count);
            final boolean success = result.getExitValue() == 0;
            final LogLevel level = success ? (delegate.hasVerboseLogging() ? LIFECYCLE : INFO) : ERROR;
            logger.log(level, "{} {}   {} {}", prefix, index, config.getModuleName().get(), config.toString());
            logger.log(level, "{} {}       {} {}", prefix, index, success ? "SUCCESS" : "FAILURE", result);
        }
    }

    protected Stream<XtcRunModule> moduleRunQueue() {
        final var modulesToRun = resolveModulesToRun();
        logger.info("{} Queued up {} module(s) to execute:", prefix, modulesToRun.size());
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
                throw buildException("ERROR: XtcRunModule was declared without a valid moduleName property: {}", m);
            }
            return true;
        }).toList();
    }

    protected Collection<XtcRunModule> resolveModulesToRun() {
        // Given the module definition in the xtcRun closure in the DSL, create their equivalent POJOs.
        if (getExtension().isEmpty()) {
            // 1) No modules were declared
            logger.warn("{} Configuration does not contain specified modules to run. Will default to 'xtcRunAll' task.", prefix);

            // 2) Examine all compiled modules for this project.
            final var allModules = resolveCompiledModules();
            if (allModules.isEmpty()) {
                // 3) We have no compiled modules for the project, return an empty execution queue.
                logger.warn("{} There is nothing in the module path to run. Aborting.", prefix);
                return emptySet();
            }

            // 4) We do have modules compiled for this project. Add them all to the execution queue.
            allModules.forEach(m -> logger.info("{}     Module '{}' added to execution queue.", prefix, m));
            return allModules;
        }

        return validatedModules();
    }

    private Collection<File> resolveCompiledModuleFiles() {
        final var allFiles = getInputModulesCompiledByProject().getAsFileTree().getFiles();
        logger.info("{} All XTC modules compiled for SourceSet '{}':", prefix, sourceSet.getName());
        allFiles.forEach(f -> logger.info("{}    {}", prefix, f.getAbsolutePath()));
        return allFiles;
    }

    private Collection<XtcRunModule> resolveCompiledModules() {
        final var moduleFiles = resolveCompiledModuleFiles();
        final var allModules = moduleFiles.stream().map(File::getAbsolutePath).map(this::createModuleNamed).toList();
        logger.info("{} Resolved {} module names or module file paths to run.", prefix, allModules.size());
        return allModules;
    }

    private XtcRunModule createModuleNamed(final String name) {
        return DefaultXtcRuntimeExtension.createModule(project, name);
    }

    @SuppressWarnings("UnusedReturnValue")
    private ExecResult runOne(final XtcRunModule runConfig, final XtcLauncher<XtcRuntimeExtension, ? extends XtcLauncherTask<XtcRuntimeExtension>> launcher, final CommandLine cmd) {
        logger.info("{} Executing resolved xtcRuntime module closure: {}", prefix, runConfig);
        final var moduleMethod = runConfig.getMethodName().get();
        if (!runConfig.hasDefaultMethodName()) {
            cmd.add("--method", moduleMethod);
        }

        final var moduleName = runConfig.getModuleName().get();
        cmd.addRaw(moduleName);
        cmd.addRaw(runConfig.getModuleArgs().get());

        final ExecResult result = launcher.apply(cmd);
        executedModules.put((DefaultXtcRunModule)runConfig, result);
        logger.info("{}    Finished executing: {}", prefix, moduleName);

        return handleExecResult(result);
    }

    @Override
    public String toString() {
        return projectName + ':' + taskName + " [class: " + getClass().getSimpleName() + ']';
    }
}
