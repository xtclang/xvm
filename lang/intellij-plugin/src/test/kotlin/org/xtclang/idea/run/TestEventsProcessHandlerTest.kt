package org.xtclang.idea.run

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xtclang.idea.lsp.TestEvent
import java.util.concurrent.ConcurrentLinkedQueue

/** The test events of a debugged test run, as IntelliJ's test console in its Tests tab reads them. */
class TestEventsProcessHandlerTest {
    @Test
    fun `output before the console attaches waits for it, and output after goes at once`() {
        val events = TestEventsProcessHandler()
        val heard = Heard(events)
        events.tested(STARTED)
        assertThat(heard.text()).isEmpty()

        events.attached()
        assertThat(heard.text()).isEqualTo(STARTED.line())
        events.output("inside a\n")
        assertThat(heard.text()).isEqualTo(STARTED.line() + "inside a\n")
        events.ended(3)
        assertThat(heard.exitCode).isEqualTo(3)
    }

    @Test
    fun `a session that ends before the console attaches ends the events once it does, after what it printed`() {
        val events = TestEventsProcessHandler()
        val heard = Heard(events)
        events.tested(FINISHED)
        events.ended(0)
        assertThat(heard.exitCode).isNull()

        events.attached()
        assertThat(heard.text()).isEqualTo(FINISHED.line())
        assertThat(heard.exitCode).isEqualTo(0)
    }

    @Test
    fun `an event goes to the test console as its line, which reads it back as the event`() {
        assertThat(STARTED.line()).startsWith(TestEvent.MARKER).endsWith("\n")
        assertThat(TestEvent.parse(STARTED.line())).isEqualTo(STARTED)
        assertThat(TestEvent.parse("inside a\n")).isNull()
        assertThat(TestEvent.parse("${TestEvent.MARKER}{\"id\":\"M:\"}")).isNull()
    }

    private companion object {
        val STARTED = TestEvent(TestEvent.STARTED, id = "M:void a()", parent = "M:", name = "a()", selector = "a", container = false)
        val FINISHED = TestEvent(TestEvent.FINISHED, id = "M:void a()", status = "passed", durationMillis = 3)
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
