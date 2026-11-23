package org.xtclang.idea.dap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xtclang.idea.lsp.DebugTaskParams
import org.xtclang.idea.lsp.ServerTest
import org.xtclang.idea.lsp.TestSelector
import org.xtclang.idea.run.XtcBuildSettings
import org.xtclang.idea.run.XtcRunConfiguration

/**
 * The task of a Gradle-mode or command-line debug launch, without an IDE: the module's Gradle run with the debug
 * properties, or `xtc run` with `--debug`.
 */
class DebugTasksTest {
    @Test
    fun `the task runs the module in a JVM of its own, with the launcher's debug option`() {
        val params =
            DebugTaskParams(
                sessionId = "s",
                module = "App",
                method = "check",
                args = listOf("one", "two"),
                debug = "connect:127.0.0.1:5005,suspend,token=t",
            )
        val command = DebugTasks.commandLine(params)
        assertThat(command.exePath).isEqualTo(XtcRunConfiguration.gradleWrapper())
        assertThat(command.parametersList.list).containsExactly(
            "-q",
            "runXtc",
            "--mode=ATTACHED",
            "--module=App",
            "--method=check",
            "--args=one,two",
            "-PxtcDebug=connect:127.0.0.1:5005,suspend,token=t",
        )

        val plain = DebugTasks.commandLine(params.copy(method = null, args = emptyList()))
        assertThat(plain.parametersList.list)
            .containsExactly("-q", "runXtc", "--mode=ATTACHED", "--module=App", "-PxtcDebug=connect:127.0.0.1:5005,suspend,token=t")
        assertThat(DebugTasks.commandLine(params.copy(buildMode = "gradle")).parametersList.list).contains("runXtc")
    }

    @Test
    fun `a module's task is its Gradle project's, as the imported model names it, which reaches a composite's included build`() {
        val params =
            DebugTaskParams(
                sessionId = "s",
                module = "json.xtclang.org",
                method = null,
                args = emptyList(),
                debug = "connect:127.0.0.1:5005,suspend,token=t",
            )
        val projects = mapOf("json.xtclang.org" to ":xdk:lib-json", "App" to ":")
        assertThat(DebugTasks.commandLine(params, projects).parametersList.list)
            .containsExactly(
                "-q",
                ":xdk:lib-json:runXtc",
                "--mode=ATTACHED",
                "--module=json.xtclang.org",
                "-PxtcDebug=connect:127.0.0.1:5005,suspend,token=t",
            )
        assertThat(DebugTasks.commandLine(params.copy(tests = emptyList()), projects).parametersList.list)
            .startsWith("-q", ":xdk:lib-json:testXtc", "--mode=ATTACHED", "--module=json.xtclang.org")
        // a root project's task, and the bare name for a module that no model names
        assertThat(DebugTasks.commandLine(params.copy(module = "App"), projects).parametersList.list).contains(":runXtc")
        assertThat(DebugTasks.commandLine(params.copy(module = "Other"), projects).parametersList.list).contains("runXtc")
        // every test module: each project's testXtc, or the bare name without a model
        assertThat(XtcBuildSettings.gradleTasks(projects, "testXtc")).containsExactly(":testXtc", ":xdk:lib-json:testXtc")
        assertThat(XtcBuildSettings.gradleTasks(emptyMap(), "testXtc")).containsExactly("testXtc")
    }

    @Test
    fun `a command-line launch runs xtc run as a command-line Run does, with the debug option`() {
        val params =
            DebugTaskParams(
                sessionId = "s",
                module = "App",
                method = "check",
                args = listOf("one", "two"),
                debug = "connect:127.0.0.1:5005,suspend,token=t",
                buildMode = DebugTasks.COMMAND_LINE,
            )
        val command = DebugTasks.commandLine(params)
        assertThat(command.exePath).isEqualTo("xtc")
        assertThat(command.parametersList.list).containsExactly(
            "run",
            "--deduce",
            "--method",
            "check",
            "--debug",
            "connect:127.0.0.1:5005,suspend,token=t",
            "App",
            "one",
            "two",
        )

        val plain = DebugTasks.commandLine(params.copy(method = null, args = emptyList()))
        assertThat(plain.parametersList.list).containsExactly("run", "--deduce", "--debug", "connect:127.0.0.1:5005,suspend,token=t", "App")

        // Run's command line is the same, without --debug
        assertThat(XtcRunConfiguration.xtcCommandLine("App", "check", listOf("one")).parametersList.list)
            .containsExactly("run", "--deduce", "--method", "check", "App", "one")
    }

    @Test
    fun `a launch with tests runs testXtc, or xtc test, for them, with the json report and the debug option`() {
        val tests = listOf(TestSelector("Inner", ServerTest.CLASS), TestSelector("Inner.works", ServerTest.TEST))
        val params =
            DebugTaskParams(
                sessionId = "s",
                module = "MathyTest",
                method = null,
                args = emptyList(),
                debug = "connect:127.0.0.1:5005,suspend,token=t",
                tests = tests,
            )
        assertThat(DebugTasks.commandLine(params).parametersList.list).containsExactly(
            "-q",
            "testXtc",
            "--mode=ATTACHED",
            "--module=MathyTest",
            "--test-class=Inner",
            "--test-method=Inner.works",
            "--test-report=json",
            "-PxtcDebug=connect:127.0.0.1:5005,suspend,token=t",
        )
        val commandLine = DebugTasks.commandLine(params.copy(buildMode = DebugTasks.COMMAND_LINE))
        assertThat(commandLine.exePath).isEqualTo("xtc")
        assertThat(commandLine.parametersList.list).containsExactly(
            "test",
            "--deduce",
            "-c",
            "Inner",
            "-t",
            "Inner.works",
            "-I",
            "xvm.xunit.report=json",
            "--debug",
            "connect:127.0.0.1:5005,suspend,token=t",
            "MathyTest",
        )
        // all of the module's tests: no selector, and Run's command line is the same, without --debug
        assertThat(XtcRunConfiguration.xtcTestCommandLine("MathyTest", emptyList()).parametersList.list)
            .containsExactly("test", "--deduce", "-I", "xvm.xunit.report=json", "MathyTest")
        assertThat(XtcRunConfiguration.gradleTestOptions(emptyList())).containsExactly("--test-report=json")
    }
}
