package org.xtclang.plugin.tasks;

import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecResult;
import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.XtcRuntimeExtension;
import org.xtclang.plugin.XtcRuntimeExtension.XtcRunModule;
import org.xtclang.plugin.internal.DefaultXtcRuntimeExtension;
import org.xtclang.plugin.internal.DefaultXtcRuntimeExtension.DefaultXtcRunModule;
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
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_JAVATOOLS_INCOMING;
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
public class XtcRunTask extends XtcLauncherTask {
    protected final XtcRuntimeExtension ext;
    protected final Provider<XtcLauncher> launcher;

    private final Map<DefaultXtcRunModule, ExecResult> executedModules; // TODO we can cache output here to if we want.

    /**
     * Create an XTC run task, currently delegating instead of inheriting the plugin project
     * delegate. We are slowly getting rid of this delegate pattern, now that the intra-plugin
     * needed types have been resolved.
     *
     * @param project Project delegate
     * @param sourceSet Source set for the code of the module to be executed.
     */
    @Inject
    public XtcRunTask(final XtcProjectDelegate project, final SourceSet sourceSet) {
        super(project, sourceSet);
        this.ext = project.xtcRuntimeExtension();
        this.executedModules = new LinkedHashMap<>();
        this.launcher = project.getProject().provider(() -> XtcLauncher.create(project.getProject(), XTC_RUNNER_CLASS_NAME, ext));
    }

    @Optional
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    FileCollection getInputDeclaredDependencyModules() {
        return project.filesFrom(incomingXtcModuleDependencies(sourceSet)); // xtcModule and xtcModuleTest dependencies declared in the project dependency { scope section
    }

    @Optional // we may have a build that only relies on modules build by other projects, or other dependencies.
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    FileCollection getInputModulesCompiledByProject() {
        return project.getXtcCompilerOutputModules(sourceSet); // The output of the XTC compiler for this project and source set.
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.ABSOLUTE)
    Provider<Directory> getInputXdkModules() {
        return project.getXdkContentsDir(); // Modules in the XDK directory, if one exists.
    }

    @Input
    ListProperty<String> getJvmArgs() {
        return ext.getJvmArgs();
    }

    @Input
    Property<Boolean> getIsFork() {
        return ext.getFork();
    }

    @Input
    Property<Boolean> getIsVerbose() {
        return ext.getVerbose();
    }

    @Input
    Property<Boolean> getDebuggerEnabled() {
        return ext.getDebugEnabled();
    }

    @Input
    Property<Boolean> getShowVersion() {
        return ext.getShowVersion();
    }

    @Input
    Property<Boolean> getUseNativeLauncher() {
        return ext.getUseNativeLauncher();
    }

    @Input
    Property<Boolean> getLogOutputs() {
        return ext.getLogOutputs();
    }

    @Input
    Property<Boolean> getAllowParallel() {
        return ext.getAllowParallel();
    }

    @Input
    List<String> getModuleNames() {
        return ext.getModuleNames();
    }

    @Input
    List<String> getModuleMethods() {
        return ext.getModuleMethods();
    }

    @Input
    List<String> getModuleArgs() {
        return ext.getModuleNames();
    }

    @TaskAction
    public void run() {
        start();
        final var cmd = new CommandLine(XTC_RUNNER_CLASS_NAME, getJvmArgs().get());
        cmd.addBoolean("-version", getShowVersion().get());
        cmd.addBoolean("-verbose", getIsVerbose().get());
        cmd.addBoolean("-debug", getDebuggerEnabled().get());
        cmd.addRepeated("-L", project.resolveModulePath(name, getInputDeclaredDependencyModules()));
        moduleRunQueue().forEach(module -> runOne(module, cmd.copy()));
        logFinishedRuns();
    }

    private void logFinishedRuns() {
        checkResolvable();
        final int count = executedModules.size();
        project.lifecycle("{} '{}' Task executed {} modules:", prefix, name, count);
        int i = 0;
        for (final var entry : executedModules.entrySet()) {
            final DefaultXtcRunModule config = entry.getKey();
            final ExecResult result = entry.getValue();
            final String index = String.format("(%2d/%2d)", ++i, count);
            final boolean success = result.getExitValue() == 0;
            final LogLevel level = success ? LogLevel.LIFECYCLE : LogLevel.ERROR;
            project.log(level, "{} '{}' {}   {} {}", prefix, name, index, config.getModuleName().get(), config.toString(isResolvable()));
            project.log(level, "{} '{}' {}       {} {}", prefix, name, index, success ? "SUCCESS" : "FAILURE", result);
        }
    }

    protected Stream<XtcRunModule> moduleRunQueue() {
        final var modulesToRun = resolveModulesToRun();
        project.lifecycle("{} '{}' Queued up {} module(s) to execute:", prefix, name, modulesToRun.size());
        return getAllowParallel().get() ? modulesToRun.parallelStream() : modulesToRun.stream();
    }

    protected Collection<XtcRunModule> resolveModulesToRun() {
        // Given the module definition in the xtcRun closure in the DSL, create their equivalent POJOs.
        if (ext.isEmpty()) {
            // 1) No modules were declared
            project.warn("{} '{}' Configuration does not contain specified modules to run. Will default to 'xtcRunAll' task.", prefix, name);

            // 2) Examine all compiled modules for this project.
            final var allModules = resolveCompiledModules();
            if (allModules.isEmpty()) {
                // 3) We have no compiled modules for the project, return an empty execution queue.
                project.warn("{} '{}' There is nothing in the module path to run. Aborting.", prefix, name);
                return emptySet();
            }

            // 4) We do have modules compiled for this project. Add them all to the execution queue.
            allModules.forEach(m -> project.lifecycle("{} '{}'    Module '{}' added to execution queue.", prefix, name, m));
            return allModules;
        }

        return ext.validatedModules();
    }

    private Collection<File> resolveCompiledModuleFiles() {
        final var allFiles = getInputModulesCompiledByProject().getAsFileTree().getFiles();
        project.info("{} All XTC modules compiled for SourceSet '{}':", prefix, sourceSet.getName());
        allFiles.forEach(f -> project.info("{}    {}", prefix, f.getAbsolutePath()));
        return allFiles;
    }

    private Collection<XtcRunModule> resolveCompiledModules() {
        final var moduleFiles = resolveCompiledModuleFiles();
        final var allModules = moduleFiles.stream().map(File::getAbsolutePath).map(this::createModuleNamed).toList();
        project.lifecycle("{} '{}' Resolved {} module names or module file paths to run.", prefix, getName(), allModules.size());
        return allModules;
    }

    private XtcRunModule createModuleNamed(final String name) {
        return DefaultXtcRuntimeExtension.createModule(project.getProject(), name);
    }

    @SuppressWarnings("UnusedReturnValue")
    private ExecResult runOne(final XtcRunModule runConfig, final CommandLine cmd) {
        project.info("{} Executing resolved xtcRuntime module closure: {}", prefix, runConfig);
        final var moduleMethod = runConfig.getMethodName().get();
        if (!runConfig.hasDefaultMethodName()) {
            cmd.add("-M", moduleMethod);
        }
        final var moduleName = runConfig.getModuleName().get();
        cmd.addRaw(moduleName);
        cmd.addRaw(runConfig.resolveModuleArgs());
        final ExecResult result = launcher.get().apply(cmd);
        executedModules.put((DefaultXtcRunModule)runConfig, result);
        project.info("{} '{}'    Finished executing: {}", prefix, name, moduleName);

        return handleExecResult(project.getProject(), result);
    }
}
