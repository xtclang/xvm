package org.xtclang.plugin.tasks;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import groovy.json.JsonOutput;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** Evaluated compiler inputs, without retaining a Project/SourceSet in a task action. */
@DisableCachingByDefault(because = "Exports machine-local absolute paths for the IDE")
public abstract class XtcLspSourceSetTask extends DefaultTask {
    @Input
    public abstract Property<String> getProjectId();

    @Input
    public abstract Property<String> getProjectPath();

    /**
     * The project's path in the whole build tree, such as {@code :xdk:lib-json} for a project of an included build, which
     * names its tasks from the root of a composite build: a bare task name reaches no included build.
     */
    @Input
    public abstract Property<String> getBuildTreePath();

    @Input
    public abstract Property<String> getProjectDirectory();

    @Input
    public abstract Property<String> getBuildFile();

    @Input
    public abstract Property<String> getSourceSetName();

    @Input
    public abstract Property<String> getResourceTask();

    @Input
    public abstract ListProperty<String> getSourceRoots();

    @Input
    public abstract ListProperty<String> getResourceSourceRoots();

    @Input
    public abstract ListProperty<String> getResourceRoots();

    @Input
    public abstract ListProperty<String> getProjectDependencies();

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileCollection getSourceFiles();

    @InputFiles
    @PathSensitive(PathSensitivity.ABSOLUTE)
    public abstract ConfigurableFileCollection getModulePath();

    /**
     * The method that the project's {@code xtcRun} runs each module it names with, by the module's name; where it names
     * a module more than once, the first time's.
     */
    @Input
    public abstract MapProperty<String, String> getRunModuleMethods();

    /** The arguments that the project's {@code xtcRun} runs each module it names with, by the module's name, as the methods are. */
    @Input
    public abstract MapProperty<String, List<String>> getRunModuleArgs();

    /** The JVM options that the project's {@code xtcRun} runs its modules with. */
    @Input
    public abstract ListProperty<String> getRunJvmArgs();

    /** What of the project's {@code xtcRun} could not be read, and is left out of the model, each with why. */
    @Input
    public abstract ListProperty<String> getRunProblems();

    /** The JVM options that the project's {@code xtcTest} runs its tests with. */
    @Input
    public abstract ListProperty<String> getTestJvmArgs();

    /** What of the project's {@code xtcTest} could not be read, and is left out of the model, each with why. */
    @Input
    public abstract ListProperty<String> getTestProblems();

    @OutputFile
    public abstract RegularFileProperty getReport();

    @TaskAction
    public void exportModel() throws IOException {
        final var model = new LinkedHashMap<String, Object>();
        model.put("projectId", getProjectId().get());
        model.put("projectPath", getProjectPath().get());
        model.put("buildTreePath", getBuildTreePath().get());
        model.put("projectDirectory", getProjectDirectory().get());
        model.put("buildFile", getBuildFile().get());
        model.put("sourceSet", getSourceSetName().get());
        model.put("sourceRoots", getSourceRoots().get());
        model.put("sourceFiles", uris(getSourceFiles().getFiles().stream().toList()));
        final var roots = getSourceRoots().get().stream().map(URI::create).map(Path::of).toList();
        model.put("moduleRoots", uris(getSourceFiles().getFiles().stream()
            .filter(file -> roots.contains(file.toPath().getParent())).toList()));
        model.put("resourceSourceRoots", getResourceSourceRoots().get());
        model.put("resourceRoots", getResourceRoots().get());
        model.put("resourceTask", getResourceTask().get());
        model.put("projectDependencies", getProjectDependencies().get());
        model.put("modulePath", uris(getModulePath().getFiles().stream().toList()));
        // The project's run settings, which apply to the modules of each of its source sets alike.
        final var arguments = getRunModuleArgs().get();
        final var modules = new LinkedHashMap<String, Object>();
        getRunModuleMethods().get().forEach((name, method) -> {
            final var module = new LinkedHashMap<String, Object>();
            module.put("methodName", method);
            module.put("moduleArgs", arguments.getOrDefault(name, List.of()));
            modules.put(name, module);
        });
        final var run = new LinkedHashMap<String, Object>();
        run.put("jvmArgs", getRunJvmArgs().get());
        run.put("modules", modules);
        run.put("problems", getRunProblems().get());
        model.put("run", run);
        final var test = new LinkedHashMap<String, Object>();
        test.put("jvmArgs", getTestJvmArgs().get());
        test.put("problems", getTestProblems().get());
        model.put("test", test);
        // replaced whole, as the model is: another build exporting the same project at once, as two editors' imports
        // of one folder, would otherwise read the report part written
        XtcLspModelTask.writeAtomically(getReport().get().getAsFile().toPath(),
            JsonOutput.prettyPrint(JsonOutput.toJson(model)) + '\n');
    }

    public static List<String> uris(final List<File> files) {
        return files.stream().map(file -> file.toURI().toString()).toList();
    }
}
