package org.xvm.plugin;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.model.ObjectFactory;
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
import org.xvm.plugin.XtcRuntimeExtension.XtcRunModule;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static org.gradle.api.plugins.ApplicationPlugin.APPLICATION_GROUP;
import static org.xvm.plugin.Constants.XTC_CONFIG_NAME_JAVATOOLS_INCOMING;
import static org.xvm.plugin.Constants.XTC_LANGUAGE_NAME;
import static org.xvm.plugin.XtcExtractXdkTask.EXTRACT_TASK_NAME;
import static org.xvm.plugin.XtcProjectDelegate.incomingXtcModuleDependencies;

/**
 * Task that runs and XTC module, given at least its name, using the module path from
 * the xtc environment.
 * TODO: Add WorkerExecutor and the Gradle Worker API to execute in parallel if there are no dependencies.
 */
public class XtcRunTask extends DefaultTask {
    static final String XTC_RUNNER_CLASS_NAME = "org.xvm.tool.Runner";

    protected final XtcProjectDelegate project;
    protected final String prefix;
    protected final String name;
    protected final Logger logger;
    protected final ObjectFactory objects;
    protected final XtcRuntimeExtension extRuntime;
    protected final SourceSet sourceSet;
    protected final XtcLauncher launcher;

    private final Map<ExecResult, XtcRunModule> executedModules; // TODO we can cache output here to if we want.

    @Inject
    public XtcRunTask(final XtcProjectDelegate project, final SourceSet moduleSourceSet) {
        this.project = project;
        this.prefix = project.prefix();
        this.name = getName();
        this.objects = project.getObjects();
        this.logger = project.getLogger();
        this.sourceSet = moduleSourceSet;
        this.extRuntime = project.xtcRuntimeExtension();
        this.executedModules = new LinkedHashMap<>();
        this.launcher = XtcLauncher.create(project, XTC_RUNNER_CLASS_NAME, getIsFork().get(), getUseNativeLauncher().get());
        configureTask(moduleSourceSet);
    }

    protected XtcRunModule createModule(final String name) {
        return DefaultXtcRuntimeExtension.createModule(project.getProject(), name);
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

    // The extract dependency and this input take care of an XDK dependency
    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public FileCollection getInputDeclaredDependencyModules() {
        // xtcModule and xtcModuleTest dependencies declared in the project dependency { scope section
        return project.filesFrom(incomingXtcModuleDependencies(sourceSet));
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public Provider<Directory> getInputModulesCompiledByProject() {
        // The output of the XTC compiler for this project and source set.
        return project.getXtcCompilerOutputDirModules(sourceSet);
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public Provider<Directory> getInputXdkModules() {
        // Modules in the XDK directory, if one exists.
        return project.getXdkContentsDir();
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
    ListProperty<XtcRunModule> getModules() {
        return extRuntime.getModules();
    }

    @TaskAction
    public void run() {
        final var args = new CommandLine(XTC_RUNNER_CLASS_NAME, getJvmArgs().get());
        args.addBoolean("-version", getShowVersion().get());
        args.addBoolean("-verbose", getIsVerbose().get());
        args.addBoolean("-debug", getDebuggerEnabled().get());

        final var modulePath = project.resolveModulePath(name, getInputDeclaredDependencyModules());
        args.addRepeated("-L", modulePath);
        withStream(resolveModulesToRun()).forEach(m -> runOne(m, args.copy()));

        final int count = executedModules.size();
        project.lifecycle("{} '{}' Executed {} modules:", prefix, name, count);
        int i = 0;
        for (final var entry : executedModules.entrySet()) {
            final var result = entry.getKey();
            final var config = entry.getValue();
            final var index = String.format("(%2d/%2d)", ++i, count);
            final var success = result.getExitValue() == 0;
            final var level = success ? LogLevel.LIFECYCLE : LogLevel.ERROR;
            project.log(level, "{} '{}' {}   {} {}", prefix, name, index, config.getModuleName().get(), config);
            project.log(level, "{} '{}' {}       {} {}", prefix, name, index, success ? "SUCCESS" : "FAILURE", result);
        }
    }

    protected Collection<File> allCompiledModules() {
        final var allFiles = getInputModulesCompiledByProject().get().getAsFileTree().getFiles();
        project.info("{} All XTC modules compiled for sourceSet {}:", prefix, sourceSet.getName());
        allFiles.forEach(f -> project.info("{}    {}", prefix, f.getAbsolutePath()));
        return allFiles;
    }

    private boolean isSequentialRun() {
        return !getAllowParallel().get();
    }

    private <T> Stream<T> withStream(final Collection<T> collection) {
        final Stream<T> stream = collection.stream();
        if (isSequentialRun()) {
            return stream;
        }
        project.warn("{} WARNING: The allowParallel flag has been set. This is untested functionality.", prefix);
        return stream.parallel();
    }

    protected Collection<XtcRunModule> resolveModulesToRun() {
        final var modules = extRuntime.getModules().get();
        if (modules.isEmpty()) {
            project.warn("{} {} Configuration does not contain any modules to run.", prefix, name);
            return emptyList();
        }
        if (!extRuntime.validateModules()) {
            throw project.buildException("ERROR: Invalid runtime modules exist. Module name not specified.");
        }
        return modules;
    }

    @SuppressWarnings("UnusedReturnValue")
    private ExecResult runOne(final XtcRunModule runConfig, final CommandLine args) {
        project.info("{} Executing resolved xtcRuntime.module closure: {}", prefix, runConfig);
        final var moduleMethod = runConfig.getMethod().get();
        if (!runConfig.hasDefaultMethodName()) {
            args.add("-M", moduleMethod);
        }

        final var moduleName = runConfig.getModuleName().get();
        args.addRaw(moduleName);

        final var moduleArgs = runConfig.getArgs().get();
        args.addRaw(moduleArgs);

        final var result = launcher.apply(args);
        result.rethrowFailure();
        executedModules.put(result, runConfig);

        return result;
    }
}
