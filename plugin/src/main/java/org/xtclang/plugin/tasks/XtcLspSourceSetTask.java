package org.xtclang.plugin.tasks;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

import groovy.json.JsonOutput;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
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

    @OutputFile
    public abstract RegularFileProperty getReport();

    @TaskAction
    public void exportModel() throws IOException {
        final var model = new LinkedHashMap<String, Object>();
        model.put("projectId", getProjectId().get());
        model.put("projectPath", getProjectPath().get());
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
        final var output = getReport().get().getAsFile().toPath();
        Files.createDirectories(output.getParent());
        Files.writeString(output, JsonOutput.prettyPrint(JsonOutput.toJson(model)) + '\n');
    }

    public static List<String> uris(final List<File> files) {
        return files.stream().map(file -> file.toURI().toString()).toList();
    }
}
