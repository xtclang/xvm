package org.xtclang.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE;
import static org.gradle.api.attributes.Category.LIBRARY;
import static org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_ARTIFACT_JAVATOOLS_JAR;
import static org.xtclang.plugin.XtcPluginConstants.XDK_CONFIG_NAME_JAVATOOLS_INCOMING;
import static org.xtclang.plugin.XtcPluginConstants.XDK_VERSION_TASK_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_TEST_TASK_NAME;

import java.io.File;
import java.util.List;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.testfixtures.ProjectBuilder;

import org.junit.jupiter.api.Test;

import org.xtclang.plugin.tasks.XtcCompileTask;
import org.xtclang.plugin.tasks.XtcTestTask;

// TODO: Add build script test toolkit tests that check a dsl runs the correct modules, and that it accepts
//   command line properties, project properties/args to control which tasks to run.
public class SimpleXtcPluginTest {

    @SuppressWarnings("SameParameterValue")
    private static Project newProject(final String name) {
        final var project = ProjectBuilder.builder().withName(name).build();
        project.setGroup("org.xtclang");
        project.setVersion("1.0");
        return project;
    }

    @Test
    public void verifyXtcProjectPluginCanBeApplied() {
        final Project project = newProject("verifyXtcProjectPluginCanBeApplied");
        final TaskContainer tasks = project.getTasks();

        final int tasksBefore = tasks.size();
        System.out.println("There are " + tasksBefore + " tasks in project '" + project.getName() + "' before plugin application:");
        tasks.forEach(task -> System.err.println('\t' + task.getName()));

        // Apply Java base plugin first (required dependency)
        final var pluginManager = project.getPluginManager();
        pluginManager.apply(org.gradle.api.plugins.JavaBasePlugin.class);
        
        // Apply only the XtcProjectPlugin which doesn't depend on build-logic plugins
        pluginManager.apply(XtcPlugin.XtcProjectPlugin.class);

        final int tasksAfter = tasks.size();
        System.out.println("There are " + tasksAfter + " tasks in project '" + project.getName() + "' after plugin application:");
        tasks.forEach(task -> System.err.println('\t' + task.getName()));

        assertTrue(tasksBefore < tasksAfter);
        assertNotNull(tasks.findByName(XDK_VERSION_TASK_NAME));
    }

    @Test
    public void verifyTestSourceSetCompilationDependsOnMainOutputAndTestsDependOnCompilation() {
        final Project project = newProject("verifyTestSourceSetCompilationDependsOnMainOutputAndTestsDependOnCompilation");
        final var pluginManager = project.getPluginManager();
        pluginManager.apply(org.gradle.api.plugins.JavaBasePlugin.class);
        pluginManager.apply(XtcPlugin.XtcProjectPlugin.class);

        final XtcCompileTask compileXtc = (XtcCompileTask) project.getTasks().getByName("compileXtc");
        final XtcCompileTask compileTestXtc = (XtcCompileTask) project.getTasks().getByName("compileTestXtc");
        final XtcTestTask testXtc = (XtcTestTask) project.getTasks().getByName(XTC_TEST_TASK_NAME);

        final var mainOutputDir = XtcProjectDelegate.getXtcSourceSetOutputDirectory(
            project,
            XtcProjectDelegate.getSourceSets(project).getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        ).get().getAsFile();

        assertEquals(SourceSet.TEST_SOURCE_SET_NAME, compileTestXtc.getCompileSourceSetName());
        assertTrue(
            compileTestXtc.getXtcModuleDependencies().getFiles().contains(mainOutputDir),
            "compileTestXtc should treat main source-set output as an input dependency"
        );

        final var testDependencies = testXtc.getTaskDependencies().getDependencies(testXtc).stream()
            .map(Task::getName)
            .toList();

        assertTrue(testDependencies.contains("compileXtc"), "testXtc should depend on compileXtc");
        assertTrue(testDependencies.contains("compileTestXtc"), "testXtc should depend on compileTestXtc");
        assertNotNull(compileXtc);
    }

    @Test
    public void verifyRebuildFalseStopsTrackingLauncherRuntimeAsCompileInput() {
        final Project project = newProject("verifyRebuildFalseStopsTrackingLauncherRuntimeAsCompileInput");
        final PluginManager pluginManager = project.getPluginManager();
        pluginManager.apply(org.gradle.api.plugins.JavaBasePlugin.class);
        pluginManager.apply(XtcPlugin.XtcProjectPlugin.class);

        final TaskProvider<Task> javaToolsJar = project.getTasks().register("produceJavaTools");
        final File javaToolsFile = project.getLayout().getBuildDirectory().file("libs/javatools.jar").get().getAsFile();
        final ConfigurableFileCollection javaToolsFiles = project.files(javaToolsFile).builtBy(javaToolsJar);
        project.getDependencies().getArtifactTypes().getByName("jar").getAttributes().attribute(
            CATEGORY_ATTRIBUTE,
            project.getObjects().named(Category.class, LIBRARY)
        );
        project.getDependencies().getArtifactTypes().getByName("jar").getAttributes().attribute(
            LIBRARY_ELEMENTS_ATTRIBUTE,
            project.getObjects().named(LibraryElements.class, XDK_CONFIG_NAME_ARTIFACT_JAVATOOLS_JAR)
        );
        project.getDependencies().add(XDK_CONFIG_NAME_JAVATOOLS_INCOMING, javaToolsFiles);

        final XtcCompileTask compileXtc = (XtcCompileTask) project.getTasks().getByName("compileXtc");

        assertTrue(
            compileXtc.getInputXtcJavaToolsConfig().getFiles().contains(javaToolsFile),
            "compileXtc should track javatools as an input while rebuild is enabled"
        );
        assertTrue(
            compileXtc.getInputLauncherRuntimeCandidates().getFiles().contains(javaToolsFile),
            "compileXtc should track launcher runtime candidates while rebuild is enabled"
        );

        compileXtc.getRebuild().set(false);

        assertTrue(
            compileXtc.getInputXtcJavaToolsConfig().isEmpty(),
            "compileXtc should ignore javatools as an input while rebuild is disabled"
        );
        assertTrue(
            compileXtc.getInputLauncherRuntimeCandidates().isEmpty(),
            "compileXtc should ignore launcher runtime candidates while rebuild is disabled"
        );

        final List<String> compileDependencies = compileXtc.getTaskDependencies().getDependencies(compileXtc).stream()
            .map(Task::getName)
            .toList();
        assertTrue(
            compileDependencies.contains("produceJavaTools"),
            "compileXtc should still depend on the javatools producer while rebuild is disabled"
        );
    }
}
