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
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecResult;
import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.XtcRuntimeExtension;
import org.xtclang.plugin.XtcRuntimeExtension.XtcRunModule;
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
import static org.gradle.api.plugins.ApplicationPlugin.APPLICATION_GROUP;
import static org.xtclang.plugin.Constants.XTC_CONFIG_NAME_JAVATOOLS_INCOMING;
import static org.xtclang.plugin.Constants.XTC_LANGUAGE_NAME;
import static org.xtclang.plugin.Constants.XTC_RUNNER_CLASS_NAME;
import static org.xtclang.plugin.XtcProjectDelegate.incomingXtcModuleDependencies;
import static org.xtclang.plugin.tasks.XtcExtractXdkTask.EXTRACT_TASK_NAME;

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

// TODO: Add WorkerExecutor and the Gradle Worker API to execute in parallel if there are no dependencies.
// Any task with zero defined outputs is not cacheable, which should be enough for all run tasks.
public class XtcRunTask extends XtcDefaultTask {
    protected final XtcRuntimeExtension extRuntime;
    protected final SourceSet sourceSet;
    protected final XtcLauncher launcher;

    private final Map<XtcRunModule, ExecResult> executedModules; // TODO we can cache output here to if we want.

    @Inject
    public XtcRunTask(final XtcProjectDelegate project, final SourceSet moduleSourceSet) {
        super(project);
        this.sourceSet = moduleSourceSet;
        this.extRuntime = project.xtcRuntimeExtension();
        this.executedModules = new LinkedHashMap<>();
        this.launcher = XtcLauncher.create(project.getProject(), XTC_RUNNER_CLASS_NAME, getIsFork().get(), getUseNativeLauncher().get());
        configureTask(moduleSourceSet);
    }

    private void configureTask(final SourceSet sourceSet) {
        setGroup(APPLICATION_GROUP);
        setDescription("Run an XTC program with a configuration supplying the module path(s).");
        dependsOn(EXTRACT_TASK_NAME);
        dependsOn(sourceSet.getCompileTaskName(XTC_LANGUAGE_NAME));
        project.info("{} Configured, dependency to tasks: {} -> {}", prefix, EXTRACT_TASK_NAME, sourceSet.getCompileTaskName(XTC_LANGUAGE_NAME));
        createStartScriptTask();
        executedModules.clear();
    }

    // TODO: Add modules {} segment to runtime DSL
    // TODO: Add a generic xtcPlugin or xtc extension, where we can set stuff like, e.g. log level for the plugin (which does not redirect)
    // TODO: For compile and runtime tasks, allow diverting stdout, with the JavaCompile pattern.
    private void createStartScriptTask() {
        // TODO we should add create and start script tasks just like in the JavaApplication plugin.
        project.info("{} All XTC module compiled for sourceSet {}:", prefix, sourceSet.getName());
    }

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public FileCollection getInputXtcJavaToolsConfig() {
        return project.getProject().files(project.getProject().getConfigurations().getByName(XTC_CONFIG_NAME_JAVATOOLS_INCOMING));
    }

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public FileCollection getInputDeclaredDependencyModules() {
        return project.filesFrom(incomingXtcModuleDependencies(sourceSet)); // xtcModule and xtcModuleTest dependencies declared in the project dependency { scope section
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public Provider<Directory> getInputModulesCompiledByProject() {
        return project.getXtcCompilerOutputDirModules(sourceSet); // The output of the XTC compiler for this project and source set.
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public Provider<Directory> getInputXdkModules() {
        return project.getXdkContentsDir(); // Modules in the XDK directory, if one exists.
    }

    @Input
    ListProperty<String> getJvmArgs() {
        return extRuntime.getJvmArgs();
    }

    @Input
    Property<Boolean> getIsFork() {
        return extRuntime.getFork();
    }

    @Input
    Property<Boolean> getIsVerbose() {
        return extRuntime.getVerbose();
    }

    @Input
    Property<Boolean> getDebuggerEnabled() {
        return extRuntime.getDebugEnabled();
    }

    @Input
    Property<Boolean> getShowVersion() {
        return extRuntime.getShowVersion();
    }

    @Input
    Property<Boolean> getUseNativeLauncher() {
        return extRuntime.getUseNativeLauncher();
    }

    @Input
    Property<Boolean> getAllowParallel() {
        return extRuntime.getAllowParallel();
    }

    @Input
    List<String> getModuleNames() {
        return extRuntime.getModuleNames();
    }

    @Input
    List<String> getModuleMethods() {
        return extRuntime.getModuleMethods();
    }

    @Input
    List<String> getModuleArgs() {
        return extRuntime.getModuleNames();
    }

    @TaskAction
    public void run() {
        final var args = new CommandLine(XTC_RUNNER_CLASS_NAME, getJvmArgs().get());
        args.addBoolean("-version", getShowVersion().get());
        args.addBoolean("-verbose", getIsVerbose().get());
        args.addBoolean("-debug", getDebuggerEnabled().get());

        final var modulePath = project.resolveModulePath(name, getInputDeclaredDependencyModules());
        args.addRepeated("-L", modulePath);

        moduleRunQueue().forEach(m -> runOne(m, args.copy()));

        final int count = executedModules.size();
        project.lifecycle("{} '{}'    Executed {} modules:", prefix, name, count);
        int i = 0;
        for (final var entry : executedModules.entrySet()) {
            final var config = entry.getKey();
            final var result = entry.getValue();
            final var index = String.format("(%2d/%2d)", ++i, count);
            final var success = result.getExitValue() == 0;
            final var level = success ? LogLevel.LIFECYCLE : LogLevel.ERROR;
            project.log(level, "{} '{}' {}   {} {}", prefix, name, index, config.getModuleName().get(), config);
            project.log(level, "{} '{}' {}       {} {}", prefix, name, index, success ? "SUCCESS" : "FAILURE", result);
        }
    }

    protected Stream<XtcRunModule> moduleRunQueue() {
        final var modulesToRun = resolveModulesToRun();
        project.lifecycle("{} '{}' Queued up {} modules to execute:", prefix, name, modulesToRun.size());
        return getAllowParallel().get() ? modulesToRun.parallelStream() : modulesToRun.stream();
    }

    protected Collection<XtcRunModule> resolveModulesToRun() {
        // Given the module definition in the xtcRun closure in the DSL, create their equivalent POJOs.
        if (extRuntime.isEmpty()) {
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

        return extRuntime.validatedModules();
    }

    private Collection<File> resolveCompiledModuleFiles() {
        final var allFiles = getInputModulesCompiledByProject().get().getAsFileTree().getFiles();
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
    private ExecResult runOne(final XtcRunModule runConfig, final CommandLine args) {
        project.info("{} Executing resolved xtcRuntime module closure: {}", prefix, runConfig);
        final var moduleMethod = runConfig.getMethodName().get();
        if (!runConfig.hasDefaultMethodName()) {
            args.add("-M", moduleMethod);
        }

        final var moduleName = runConfig.getModuleName().get();
        args.addRaw(moduleName);

        final var moduleArgs = runConfig.getArgs().get();
        args.addRaw(moduleArgs);

        final var result = launcher.apply(args);
        executedModules.put(runConfig, result);
        project.info("{} '{}'    Finished executing: {}", prefix, name, moduleName);

        return handleExecResult(project.getProject(), result);
    }
}
