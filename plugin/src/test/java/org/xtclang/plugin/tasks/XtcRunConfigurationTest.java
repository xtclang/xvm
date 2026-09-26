package org.xtclang.plugin.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.gradle.api.GradleException;
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

    @Test
    void testFailuresRetainTheirCause() {
        final var task = newProject().getTasks().named("testXtc", XtcTestTask.class).get();
        task.getParallel().set(true);

        final var failure = assertThrows(GradleException.class, task::executeTask);
        final var cause = assertInstanceOf(UnsupportedOperationException.class, failure.getCause());
        assertTrue(cause.getMessage().contains("Parallel module execution"));
    }

    @Test
    void modulePropertyOverridesAreTaskLocal() {
        final var project = newProject();
        final var tasks = project.getTasks();
        final var run = tasks.named("runXtc", XtcRunTask.class).get();
        final var other = tasks.register("otherRun", XtcRunTask.class, project).get();
        final var extension = XtcProjectDelegate.resolveXtcRuntimeExtension(project);
        extension.moduleName("Shared");

        run.getModules().empty();

        assertTrue(run.isEmpty());
        assertEquals("Shared", other.getModules().get().getFirst().getModuleName().get());
        assertEquals(1, extension.size());
    }

    @Test
    void explicitEmptyModuleListOverridesExtension() {
        final var project = newProject();
        final var run = project.getTasks().named("runXtc", XtcRunTask.class).get();
        final var extension = XtcProjectDelegate.resolveXtcRuntimeExtension(project);
        extension.moduleName("Shared");

        run.setModules(List.of());

        assertTrue(run.isEmpty());
        assertEquals(0, run.size());
        assertEquals(1, extension.size());
    }

    @Test
    void localModuleDslReplacesInheritedModules() {
        final var project = newProject();
        final var run = project.getTasks().named("runXtc", XtcRunTask.class).get();
        final var extension = XtcProjectDelegate.resolveXtcRuntimeExtension(project);
        extension.moduleName("Shared");

        run.moduleNames("LocalOne", "LocalTwo");

        assertEquals(List.of("LocalOne", "LocalTwo"), run.getModules().get().stream()
            .map(module -> module.getModuleName().get()).toList());
        assertEquals(1, extension.size());
    }

    @Test
    void inheritedModulesFollowLateExtensionChanges() {
        final var project = newProject();
        final var run = project.getTasks().named("runXtc", XtcRunTask.class).get();
        final var extension = XtcProjectDelegate.resolveXtcRuntimeExtension(project);
        extension.moduleName("First");
        assertEquals(1, run.size());
        extension.moduleName("Second");
        assertEquals(2, run.size());
    }

    @Test
    void cliMethodAndArgumentsOverrideConfiguredModules() {
        final var project = newProject();
        final var run = project.getTasks().named("runXtc", XtcRunTask.class).get();
        final var configured = run.module(module -> {
            module.getModuleName().set("Example");
            module.moduleArgs("original");
        });
        run.setCliMethodName("verify");
        run.setCliModuleArgs("one,two");

        final var selected = run.resolveModulesToRunFromModulePath(List.of()).getFirst();
        assertEquals("verify", selected.getMethodName().get());
        assertEquals(List.of("one", "two"), selected.getModuleArgs().get());
        assertEquals("run", configured.getMethodName().get());
        assertEquals(List.of("original"), configured.getModuleArgs().get());
    }

    @Test
    void explicitEmptyCliArgumentsClearConfiguredArguments() {
        final var run = newProject().getTasks().named("runXtc", XtcRunTask.class).get();
        run.module(module -> {
            module.getModuleName().set("Example");
            module.moduleArgs("original");
        });
        run.setCliModuleArgs("");

        assertEquals(List.of(), run.resolveModulesToRunFromModulePath(List.of()).getFirst().getModuleArgs().get());
    }

    @Test
    void configuredArgumentsSurviveAbsentCliOverride() {
        final var run = newProject().getTasks().named("runXtc", XtcRunTask.class).get();
        run.module(module -> {
            module.getModuleName().set("Example");
            module.moduleArgs("original");
        });

        assertFalse(run.getCliModuleArgs().isPresent());
        assertEquals(List.of("original"), run.resolveModulesToRunFromModulePath(List.of()).getFirst().getModuleArgs().get());
    }

    private static Project newProject() {
        final var project = ProjectBuilder.builder().build();
        project.setVersion("1.0");
        project.getPluginManager().apply(XtcPlugin.class);
        return project;
    }
}
