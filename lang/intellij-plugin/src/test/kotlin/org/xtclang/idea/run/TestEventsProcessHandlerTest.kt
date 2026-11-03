package org.xtclang.idea.run

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

/** The test events of a debugged test run, as IntelliJ's test console in its Tests tab reads them. */
class TestEventsProcessHandlerTest {
    @Test
    fun `output before the console attaches waits for it, and output after goes at once`() {
        val events = TestEventsProcessHandler()
        val heard = Heard(events)
        events.output("##teamcity[testStarted name='a']\n")
        assertThat(heard.text()).isEmpty()

        events.attached()
        assertThat(heard.text()).isEqualTo("##teamcity[testStarted name='a']\n")
        events.output("inside a\n")
        assertThat(heard.text()).isEqualTo("##teamcity[testStarted name='a']\ninside a\n")
        events.ended(3)
        assertThat(heard.exitCode).isEqualTo(3)
    }

    @Test
    fun `a session that ends before the console attaches ends the events once it does, after what it printed`() {
        val events = TestEventsProcessHandler()
        val heard = Heard(events)
        events.output("##teamcity[testFinished name='a']\n")
        events.ended(0)
        assertThat(heard.exitCode).isNull()

        events.attached()
        assertThat(heard.text()).isEqualTo("##teamcity[testFinished name='a']\n")
        assertThat(heard.exitCode).isEqualTo(0)
    }

    private class Heard(
        events: TestEventsProcessHandler,
    ) : ProcessListener {
        private val texts = ConcurrentLinkedQueue<String>()

        @Volatile
        var exitCode: Int? = null

        init {
            events.addProcessListener(this)
        }

        fun text() = texts.joinToString("")

        override fun onTextAvailable(
            event: ProcessEvent,
            outputType: Key<*>,
        ) {
            if (outputType == ProcessOutputTypes.STDOUT) texts += event.text
        }

        override fun processTerminated(event: ProcessEvent) {
            exitCode = event.exitCode
        }
    }
}
