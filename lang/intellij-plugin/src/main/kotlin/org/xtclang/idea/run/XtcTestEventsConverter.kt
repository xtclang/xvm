package org.xtclang.idea.run

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.GeneralTestEventsProcessor
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.execution.testframework.sm.runner.events.TestFailedEvent
import com.intellij.execution.testframework.sm.runner.events.TestFinishedEvent
import com.intellij.execution.testframework.sm.runner.events.TestIgnoredEvent
import com.intellij.execution.testframework.sm.runner.events.TestStartedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteFinishedEvent
import com.intellij.execution.testframework.sm.runner.events.TestSuiteStartedEvent
import com.intellij.openapi.util.Key
import org.xtclang.idea.lsp.TestEvent

/**
 * Reads a test run's output for IntelliJ's test console: each line of its standard output that is one of xunit's
 * events ([TestEvent]) is an event of the console's tree, built from the events' IDs, and the rest is output, of the
 * test that runs. A test's location is `xunit://<selector>`, which [XtcTestLocator] finds. The standard output is read
 * in whole lines, as an event's may come in pieces.
 */
internal class XtcTestEventsConverter(
    testFrameworkName: String,
    properties: TestConsoleProperties,
) : OutputToGeneralTestEventsConverter(testFrameworkName, properties) {
    /** The tests and containers that started and have not finished, by their IDs. */
    private val running = HashMap<String, Node>()

    /** The standard output after its last line end, which waits for the rest of its line. */
    private val partial = StringBuilder()

    private class Node(
        val name: String,
        val container: Boolean,
    )

    override fun process(
        text: String,
        outputType: Key<*>,
    ) {
        if (outputType != ProcessOutputTypes.STDOUT) return super.process(text, outputType)
        synchronized(partial) {
            partial.append(text)
            val end = partial.lastIndexOf("\n") + 1
            val lines = partial.substring(0, end)
            partial.delete(0, end)
            lines.lineSequence().filter { it.isNotEmpty() }.forEach { line(it, outputType) }
        }
    }

    override fun flushBufferOnProcessTermination(code: Int) {
        synchronized(partial) {
            partial.toString().takeIf { it.isNotEmpty() }?.let { line(it, ProcessOutputTypes.STDOUT) }
            partial.setLength(0)
        }
        super.flushBufferOnProcessTermination(code)
    }

    /** A whole line of the standard output, without its line end: an event, or output. */
    private fun line(
        text: String,
        outputType: Key<*>,
    ) {
        val event = TestEvent.parse(text)
        if (event == null) {
            super.process("$text\n", outputType)
        } else {
            processor?.let { apply(it, event) }
        }
    }

    private fun apply(
        processor: GeneralTestEventsProcessor,
        event: TestEvent,
    ) {
        when (event.event) {
            TestEvent.STARTED -> start(processor, event)

            TestEvent.FINISHED -> finish(processor, event)

            TestEvent.SKIPPED -> {
                start(processor, event)
                finish(processor, event.copy(status = TestEvent.SKIPPED))
            }

            // the format's version, and the events it does not know
            else -> {}
        }
    }

    private fun start(
        processor: GeneralTestEventsProcessor,
        event: TestEvent,
    ) {
        val id = event.id ?: return
        val node = Node(event.name ?: id, event.container == true)
        running[id] = node
        val parent = event.parent ?: ROOT
        val location = "${XtcTestLocator.PROTOCOL}://${event.selector.orEmpty()}"
        if (node.container) {
            processor.onSuiteStarted(TestSuiteStartedEvent(node.name, id, parent, location, null, null, null, true))
        } else {
            processor.onTestStarted(TestStartedEvent(node.name, id, parent, location, null, null, null, true))
        }
    }

    private fun finish(
        processor: GeneralTestEventsProcessor,
        event: TestEvent,
    ) {
        val id = event.id ?: return
        val node = running.remove(id) ?: return
        if (node.container) {
            processor.onSuiteFinished(TestSuiteFinishedEvent(node.name, id))
            return
        }
        when (event.status) {
            FAILED, ABORTED ->
                processor.onTestFailure(
                    TestFailedEvent(
                        node.name,
                        id,
                        event.message.orEmpty(),
                        event.details,
                        event.status == ABORTED,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false,
                        event.durationMillis ?: -1,
                    ),
                )

            TestEvent.SKIPPED -> processor.onTestIgnored(TestIgnoredEvent(node.name, id, event.message.orEmpty(), null))
        }
        processor.onTestFinished(TestFinishedEvent(node.name, id, event.durationMillis))
    }

    private companion object {
        /** The ID of the console tree's root, the parent of the run's root. */
        const val ROOT = "0"

        const val FAILED = "failed"

        /** A test that ended in an exception other than a failed assertion, an error in IntelliJ's console. */
        const val ABORTED = "aborted"
    }
}
