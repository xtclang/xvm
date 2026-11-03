package org.xtclang.idea.dap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xtclang.idea.lsp.DebugTaskParams
import org.xtclang.idea.run.XtcRunConfiguration

/**
 * The task of a Gradle-mode or command-line debug launch, without an IDE: the module's Gradle run with the debug
 * properties, or `xtc run` with `--debug`.
 */
class DebugTasksTest {
    @Test
    fun `the task runs the module in a JVM of its own, with the launcher's debug option and the agent`() {
        val params =
            DebugTaskParams(
                sessionId = "s",
                module = "App",
                method = "check",
                args = listOf("one", "two"),
                debug = "connect:127.0.0.1:5005,suspend,token=t",
                agent = "/x/bin/xtc-lsp-server.jar",
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
            "-PxtcDebugAgent=/x/bin/xtc-lsp-server.jar",
        )

        val plain = DebugTasks.commandLine(params.copy(method = null, args = emptyList(), agent = null))
        assertThat(plain.parametersList.list)
            .containsExactly("-q", "runXtc", "--mode=ATTACHED", "--module=App", "-PxtcDebug=connect:127.0.0.1:5005,suspend,token=t")
        assertThat(DebugTasks.commandLine(params.copy(buildMode = "gradle")).parametersList.list).contains("runXtc")
    }

    @Test
    fun `a command-line launch runs xtc run as a command-line Run does, with the debug option and the agent in it`() {
        val params =
            DebugTaskParams(
                sessionId = "s",
                module = "App",
                method = "check",
                args = listOf("one", "two"),
                debug = "connect:127.0.0.1:5005,suspend,token=t",
                agent = "/x/bin/xtc-lsp-server.jar",
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
            "connect:127.0.0.1:5005,suspend,token=t,agent=/x/bin/xtc-lsp-server.jar",
            "App",
            "one",
            "two",
        )

        val plain = DebugTasks.commandLine(params.copy(method = null, args = emptyList(), agent = null))
        assertThat(plain.parametersList.list).containsExactly("run", "--deduce", "--debug", "connect:127.0.0.1:5005,suspend,token=t", "App")

        // Run's command line is the same, without --debug
        assertThat(XtcRunConfiguration.xtcCommandLine("App", "check", listOf("one")).parametersList.list)
            .containsExactly("run", "--deduce", "--method", "check", "App", "one")
    }
}
