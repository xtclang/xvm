package org.xtclang.plugin.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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

    @Test
    void testCliSelectionOverridesAutomaticDiscovery() {
        final var project = newProject();
        final var task = project.getTasks().named("testXtc", XtcTestTask.class).get();
        task.setCliModuleName("SelectedTest");
        task.setCliMethodName("verify");
        task.setCliModuleArgs("one,two");

        final var modules = task.resolveModulesToRunFromModulePath(List.of());
        assertEquals(1, modules.size());
        final var selected = modules.getFirst();
        assertEquals("SelectedTest", selected.getModuleName().get());
        assertEquals("verify", selected.getMethodName().get());
        assertEquals(List.of("one", "two"), selected.getModuleArgs().get());
    }

    @Test
    void taskParallelSettingDoesNotMutateOtherTasks() {
        final var project = newProject();
        final var tasks = project.getTasks();
        final var run = tasks.named("runXtc", XtcRunTask.class).get();
        final var other = tasks.register("otherRun", XtcRunTask.class, project).get();
        run.getParallel().set(true);

        assertTrue(run.getParallel().get());
        assertFalse(other.getParallel().get());
        assertFalse(XtcProjectDelegate.resolveXtcRuntimeExtension(project).getParallel().get());
    }

    private static Project newProject() {
        final var project = ProjectBuilder.builder().build();
        project.setVersion("1.0");
        project.getPluginManager().apply(XtcPlugin.class);
        return project;
    }
}
