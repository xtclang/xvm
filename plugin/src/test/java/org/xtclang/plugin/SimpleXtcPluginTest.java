package org.xtclang.plugin;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.xtclang.plugin.XtcPluginConstants.XDK_VERSION_TASK_NAME;

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.testfixtures.ProjectBuilder;

import org.junit.jupiter.api.Test;

// TODO: Add build script test toolkit tests that check a dsl runs the correct modules, and that it accepts
//   command line properties, project properties/args to control which tasks to run.
public class SimpleXtcPluginTest {
    private static final String XTC_PLUGIN_ID = "org.xtclang.xtc-plugin";

    private static Project newProject(final String name) {
        final var project = ProjectBuilder.builder().withName(name).build();
        project.setGroup("org.xtclang");
        project.setVersion("1.0");
        return project;
    }

    @Test
    public void verifyPostPluginApplicationState() {
        final Project project = newProject("verifyPostPluginApplicationState");
        final TaskContainer tasks = project.getTasks();

        final int tasksBefore = tasks.size();
        System.out.println("There are " + tasksBefore + " tasks in project '" + project.getName() + "' before plugin application:");
        tasks.forEach(task -> System.err.println('\t' + task.getName()));

        project.getPluginManager().apply(XTC_PLUGIN_ID);

        final int tasksAfter = tasks.size();
        System.out.println("There are " + tasksAfter + " tasks in project '" + project.getName() + "' after plugin application:");
        tasks.forEach(task -> System.err.println('\t' + task.getName()));

        assertTrue(tasksBefore < tasksAfter);
        assertNotNull(tasks.findByName(XDK_VERSION_TASK_NAME));
    }
}
