package org.xtclang.idea.run

import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.xtclang.idea.LightEditor
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.hours

/**
 * Run All Ecstasy Tests: the test modules' runs, one after another, as one process, which Stop ends without starting
 * another; and the Project window's folders it is offered on.
 */
class AllTestModulesTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor()

    private val started = CopyOnWriteArrayList<String>()

    @Test
    fun `the runs go one after another, each one's output in turn, and the process ends with the last`() {
        val all = SequentialProcessHandler(CompletableFuture.completedFuture(listOf(run("one\n", 0), run("two\n", 0))))
        val output = listen(all)
        all.startNotify()

        assertThat(all.waitFor(BOUND)).isTrue()
        assertThat(started).containsExactly("one\n", "two\n")
        assertThat(output.joinToString("")).isEqualTo("one\ntwo\n")
        assertThat(all.exitCode).isEqualTo(0)
    }

    @Test
    fun `a run that fails fails the process, and the runs after it still run`() {
        val all = SequentialProcessHandler(CompletableFuture.completedFuture(listOf(run("one\n", 3), run("two\n", 0))))
        all.startNotify()

        assertThat(all.waitFor(BOUND)).isTrue()
        assertThat(started).containsExactly("one\n", "two\n")
        assertThat(all.exitCode).isEqualTo(1)
    }

    @Test
    fun `Stop stops the run that runs and starts no other`() {
        // the first run ends only when it is stopped
        val all = SequentialProcessHandler(CompletableFuture.completedFuture(listOf(run("one\n", null), run("two\n", 0))))
        all.startNotify()
        assertThat(started).containsExactly("one\n")

        all.destroyProcess()
        assertThat(all.waitFor(BOUND)).isTrue()
        assertThat(started).containsExactly("one\n")
        assertThat(all.isProcessTerminated).isTrue()
    }

    @Test
    fun `it is offered on the project's folder and on the folders down to its test sources, and not beside them`() {
        runInEdtAndWait {
            val dirs = editor.fixture.tempDirFixture
            val tests = dirs.findOrCreateDir("project/src/test/x")
            val main = dirs.findOrCreateDir("project/src/main/x")
            val root = checkNotNull(tests.parent.parent.parent)
            val holds = { dir: VirtualFile -> XtcAllTestsConfigurationProducer.holdsTests(dir, root) }

            assertThat(listOf(root, tests.parent.parent, tests.parent, tests).map(holds)).containsOnly(true)
            assertThat(holds(main)).isFalse()
            assertThat(holds(main.parent)).isFalse()
            // a project without test sources has no Run All
            assertThat(XtcAllTestsConfigurationProducer.holdsTests(main, main.parent.parent)).isFalse()
        }
    }

    /** A run that prints [text] as it starts, and ends with [exitCode] then, or, for none, only when stopped. */
    private fun run(
        text: String,
        exitCode: Int?,
    ): () -> ProcessHandler =
        {
            object : ProcessHandler() {
                init {
                    addProcessListener(
                        object : ProcessListener {
                            override fun startNotified(event: ProcessEvent) {
                                started += text
                                notifyTextAvailable(text, ProcessOutputTypes.STDOUT)
                                exitCode?.let(::notifyProcessTerminated)
                            }
                        },
                    )
                }

                override fun destroyProcessImpl() = notifyProcessTerminated(143)

                override fun detachProcessImpl() = notifyProcessDetached()

                override fun detachIsDefault() = false

                override fun getProcessInput(): OutputStream? = null
            }
        }

    private fun listen(handler: ProcessHandler): List<String> =
        CopyOnWriteArrayList<String>().also { output ->
            handler.addProcessListener(
                object : ProcessListener {
                    override fun onTextAvailable(
                        event: ProcessEvent,
                        outputType: Key<*>,
                    ) {
                        if (outputType == ProcessOutputTypes.STDOUT) output += event.text
                    }
                },
            )
        }

    private companion object {
        /** A bound against a hang, which a run that ends never reaches. */
        val BOUND = 1.hours.inWholeMilliseconds
    }
}
