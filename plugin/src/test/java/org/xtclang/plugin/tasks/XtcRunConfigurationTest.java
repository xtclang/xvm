package org.xtclang.plugin.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;

import org.junit.jupiter.api.Test;

import org.xtclang.plugin.XtcPlugin;
import org.xtclang.plugin.XtcProjectDelegate;
import org.xtclang.plugin.launchers.ExecutionMode;

class XtcRunConfigurationTest {
    @Test
    void testTaskInheritsTestLauncherSettings() {
        final var project = newProject();
        final var tasks = project.getTasks();
        final var test = tasks.named("testXtc", XtcTestTask.class).get();
        final var run = tasks.named("runXtc", XtcRunTask.class).get();
        final var extension = XtcProjectDelegate.resolveXtcTestExtension(project);

        extension.getJit().set(true);
        extension.getVerbose().set(true);
        extension.getExecutionMode().set(ExecutionMode.DIRECT);
        extension.getStdoutPath().set("test.out");

        assertTrue(test.getJit().get());
        assertTrue(test.getVerbose().get());
        assertEquals(ExecutionMode.DIRECT, test.getExecutionMode().get());
        assertEquals("test.out", test.getStdoutPath().getOrNull());
        assertFalse(run.getJit().get());
        assertFalse(run.getVerbose().get());
        assertFalse(run.hasStdoutRedirect());
    }

    private static Project newProject() {
        final var project = ProjectBuilder.builder().build();
        project.setVersion("1.0");
        project.getPluginManager().apply(XtcPlugin.class);
        return project;
    }
}
