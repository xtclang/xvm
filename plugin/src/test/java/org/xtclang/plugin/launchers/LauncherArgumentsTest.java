package org.xtclang.plugin.launchers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.gradle.testfixtures.ProjectBuilder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import org.xtclang.plugin.XtcPlugin;
import org.xtclang.plugin.tasks.XtcCompileTask;
import org.xtclang.plugin.tasks.XtcRunTask;
import org.xtclang.plugin.tasks.XtcTestTask;

class LauncherArgumentsTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1.2.3-SNAPSHOT"})
    void compileVersionIsOptionalInBothLaunchers(final String version) {
        final var project = ProjectBuilder.builder().build();
        project.setVersion("1.0");
        project.getPluginManager().apply(XtcPlugin.class);
        final var task = project.getTasks().named("compileXtc", XtcCompileTask.class).get();
        task.getXtcVersion().set(project.provider(() -> version));

        final var direct = DirectStrategy.createCompileRequest(task);
        final var forked = List.of(new ForkedCommandLineBuilder().buildCompilerArgs(task));
        if (version == null || version.isEmpty()) {
            assertNull(direct.xtcVersion());
            assertFalse(forked.contains("--set-version"));
        } else {
            assertEquals("1.2.3+SNAPSHOT", direct.xtcVersion());
            assertTrue(forked.contains("--set-version"));
            assertEquals(direct.xtcVersion(), forked.get(forked.indexOf("--set-version") + 1));
        }
    }

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

    @Test
    void testSelectionsAndTheReportReachTheTestRunnerBeforeTheModule() {
        final var project = ProjectBuilder.builder().build();
        project.setVersion("1.0");
        project.getPluginManager().apply(XtcPlugin.class);
        final var test = project.getTasks().named("testXtc", XtcTestTask.class).get();
        final var builder = new ForkedCommandLineBuilder();
        final var plain = List.of(builder.buildTestRunnerArgs(test, "ExampleTest", List.of()));
        assertFalse(plain.contains("-c") || plain.contains("-t") || plain.contains("-p") || plain.contains("-I"), plain.toString());

        test.setTestPackageOption(List.of("tests"));
        test.setTestClassOption(List.of("Inner", "tests.MathTest"));
        test.setTestMethodOption(List.of("Inner.works"));
        test.setTestReportOption("json");
        final var args = List.of(builder.buildTestRunnerArgs(test, "ExampleTest", List.of()));
        final var selections = List.of("-p", "tests", "-c", "Inner", "-c", "tests.MathTest", "-t", "Inner.works");
        final int start = args.indexOf("-p");
        assertEquals(selections, args.subList(start, start + selections.size()), args.toString());
        assertEquals("xvm.xunit.report=json", args.get(args.indexOf("-I") + 1));
        assertTrue(args.indexOf("-I") < args.indexOf("--no-recompile"), args.toString());
        assertEquals("ExampleTest", args.get(args.indexOf("--no-recompile") + 1));
    }
}
