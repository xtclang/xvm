package org.xtclang.plugin.tasks;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/** One atomic report for the active projects, so removed projects cannot leave stale IDE entries. */
@DisableCachingByDefault(because = "Exports machine-local evaluated Gradle inputs")
public abstract class XtcLspModelTask extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getReports();
    @OutputFile
    public abstract RegularFileProperty getModel();

    @TaskAction
    public void exportModel() throws IOException {
        final var sourceSets = getReports().getFiles().stream().sorted()
            .map(file -> new JsonSlurper().parse(file)).toList();
        final var output = getModel().get().getAsFile().toPath();
        Files.createDirectories(output.getParent());
        final var temporary = Files.createTempFile(output.getParent(), "model-", ".json");
        try {
            Files.writeString(temporary, JsonOutput.prettyPrint(JsonOutput.toJson(
                Map.of("schemaVersion", 1, "sourceSets", sourceSets))) + '\n');
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
