package org.xtclang.plugin.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.api.tasks.Copy;
import org.gradle.testfixtures.ProjectBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.xtclang.plugin.XtcPlugin;
import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.XtcSourceDirectorySet;

class SourceSetWiringTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"main", "test", "integration"})
    void compilerAndSourceSetFollowProcessedResourceDestination(final String sourceSetName) {
        final var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        project.setVersion("1.0");
        project.getPluginManager().apply(XtcPlugin.class);
        final var sourceSet = XtcProjectDelegate.getSourceSets(project).maybeCreate(sourceSetName);
        final var compile = project.getTasks().named(XtcProjectDelegate.getCompileTaskName(sourceSet), XtcCompileTask.class).get();
        final var resources = project.getTasks().named(sourceSet.getTaskName("process", "XtcResources"), Copy.class).get();
        final var original = resources.getDestinationDir();
        assertEquals(original, compile.getResourceDirectoryInternal().getAsFile());

        // Change the producer after both tasks have been realized, then relocate the build directory.
        resources.into(project.getLayout().getBuildDirectory().dir("processed/" + sourceSetName));
        project.getLayout().getBuildDirectory().set(project.file("relocated-build"));
        final var expected = project.file("relocated-build/processed/" + sourceSetName);
        assertEquals(expected, compile.getResourceDirectoryInternal().getAsFile());
        assertEquals(expected, compile.getResourceDirectory().get().getAsFile());
        assertTrue(sourceSet.getOutput().getDirs().contains(expected));
        assertFalse(sourceSet.getOutput().getDirs().contains(original));
    }

    @Test
    void lateSourceSetsWireCompilationDependenciesAndExistingLaunchers() throws IOException {
        final var project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        project.setVersion("1.0");
        project.getPluginManager().apply(XtcPlugin.class);
        final var tasks = project.getTasks();
        final var run = tasks.named("runXtc", XtcRunTask.class).get();
        final var extra = XtcProjectDelegate.getSourceSets(project).create("integration");

        assertNotNull(extra.getExtensions().findByType(XtcSourceDirectorySet.class));
        final var dependency = Files.writeString(directory.resolve("dependency.xtc"), "dependency").toFile();
        project.getDependencies().add("xtcModuleIntegration", project.files(dependency));
        final var compile = tasks.named("compileIntegrationXtc", XtcCompileTask.class).get();

        assertTrue(compile.getXtcModuleDependencies().contains(dependency));
        assertTrue(run.getXtcModuleDependencies().contains(dependency));
        assertTrue(run.getSourceSetNames().contains("integration"));
        assertTrue(run.getInputModulesCompiledByProject().contains(project.file("build/xtc/integration/lib")));
        assertTrue(run.getTaskDependencies().getDependencies(run).contains(compile));
        assertTrue(compile.getTaskDependencies().getDependencies(compile)
            .contains(tasks.named("processIntegrationXtcResources").get()));
    }
}
