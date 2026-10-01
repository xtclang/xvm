package org.xtclang.idea.playbook

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.seconds

internal fun ParityScenarios.progressCases() {
    case("X145") { data ->
        write(data.string("file"), data.string("source"))
        configure(
            listOf(
                SharedScenarios.SourceModule(
                    data.string("module"),
                    uri(data.string("file")),
                    emptyList(),
                ),
            ),
        )
        val document = open(data.string("file"))
        val title = "Ecstasy: finding references"
        val large =
            "module ${data.string("module")} {\n    static Int value = 1;\n" +
                (0 until data["methods"].asInt).joinToString("\n") {
                    "    Int read$it() { return value; }"
                } +
                "\n}\n"

        fun visible() =
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    utility(ProgressUi::class).visible(singleProject(), title)
                }
            }

        fun start(text: String): ClientFuture {
            replace(document, text, settle = false)
            flush(document)
            val pending =
                protocol.request(
                    "textDocument/references",
                    document.params(text.indexOf("value")) +
                        mapOf("context" to mapOf("includeDeclaration" to true)),
                )
            with(driver) {
                awaitUi("real compiler reference progress", 20.seconds) {
                    check(!pending.isDone()) { "Workload completed before progress was exercised" }
                    visible()
                }
                withContext(OnDispatcher.EDT) { utility(ProgressUi::class).show(singleProject()) }
            }
            return pending
        }
        try {
            val canceled = start(large)
            with(driver) {
                withContext(OnDispatcher.EDT) {
                    check(utility(ProgressUi::class).cancel(singleProject(), title))
                }
            }
            val outcome = runCatching { protocol.await("textDocument/references", canceled) }
            check(
                outcome.exceptionOrNull().let {
                    it is CancellationException || (it is ClientRequestFailure && it.code == -32800)
                },
            ) {
                "Cancel must terminate the pending request: ${outcome.exceptionOrNull() ?: "completed before cancellation"}"
            }
            with(driver) { awaitUi("canceled progress disappears", 15.seconds) { !visible() } }
            check(!query("textDocument/hover", document, large.indexOf("value")).isJsonNull)
            clean(document)
            val wrapper = protocol.server()
            val previous = requireNotNull(wrapper.getCurrentProcessId())
            val pending = start(large + "// unsaved restart\n")
            wrapper.restart()
            with(driver) {
                awaitUi("old process exits and pending request retires", 30.seconds) {
                    pending.isDone() && !utility(ProgressUi::class).alive(previous)
                }
            }
            check(protocol.server().getCurrentProcessId() != previous)
            check(document.text == large + "// unsaved restart\n")
            replace(document, data.string("source"))
            clean(document)
            with(driver) { awaitUi("retired progress disappears", 15.seconds) { !visible() } }
        } finally {
            with(driver) {
                withContext(OnDispatcher.EDT) { utility(ProgressUi::class).hide(singleProject()) }
            }
        }
    }
}

@Remote("org.xtclang.idea.playbook.probe.ProgressUi", plugin = "org.xtclang.playbook.probe")
internal interface ProgressUi {
    fun visible(
        project: Project,
        title: String,
    ): Boolean

    fun show(project: Project)

    fun hide(project: Project)

    fun cancel(
        project: Project,
        title: String,
    ): Boolean

    fun alive(pid: Long): Boolean
}
