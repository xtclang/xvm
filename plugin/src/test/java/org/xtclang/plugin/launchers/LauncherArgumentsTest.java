package org.xtclang.plugin.launchers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.gradle.testfixtures.ProjectBuilder;

import org.junit.jupiter.api.Test;

import org.xtclang.plugin.XtcPlugin;
import org.xtclang.plugin.tasks.XtcRunTask;

class LauncherArgumentsTest {
    @Test
    void selectedModuleMethodReachesBothLaunchers() {
        final var project = ProjectBuilder.builder().build();
        project.setVersion("1.0");
        project.getPluginManager().apply(XtcPlugin.class);
        final var task = project.getTasks().named("runXtc", XtcRunTask.class).get();
        final var module = task.module(it -> {
            it.getModuleName().set("Example");
            it.getMethodName().set("verify");
            it.moduleArgs("one", "two");
        });

        final var direct = DirectStrategy.createRunRequest(task, module);
        assertEquals("verify", direct.methodName());
        assertEquals(List.of("one", "two"), direct.moduleArgs());

        final var forked = List.of(new ForkedCommandLineBuilder().buildRunnerArgs(task, module));
        assertTrue(forked.contains("-M"), forked.toString());
        assertEquals("verify", forked.get(forked.indexOf("-M") + 1));
        assertEquals(List.of("Example", "one", "two"), forked.subList(forked.size() - 3, forked.size()));
    }
}
