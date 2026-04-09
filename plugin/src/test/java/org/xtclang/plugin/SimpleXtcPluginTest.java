package org.xtclang.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.xtclang.plugin.XtcPluginConstants.XDK_VERSION_TASK_NAME;
import static org.xtclang.plugin.XtcPluginConstants.XTC_TEST_TASK_NAME;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskContainer;
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
}
