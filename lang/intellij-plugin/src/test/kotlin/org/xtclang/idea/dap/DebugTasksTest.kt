package org.xtclang.idea.dap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xtclang.idea.lsp.DebugTaskParams
import org.xtclang.idea.run.XtcRunConfiguration

/** The Gradle task of a Gradle-mode debug launch, without an IDE: the module's run, with the debug properties. */
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
    }
}
