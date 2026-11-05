package org.xtclang.idea.playbook

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.xtclang.idea.playbook.probe.HANG_LIMIT
import org.xtclang.idea.playbook.probe.HANG_SECONDS
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit.SECONDS

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
        val title = "Finding references"

        /** Restarts the language server as a user does, with the CLI, which reaches the editor and asks it to. */
        fun restart() {
            val script =
                Path
                    .of(System.getProperty("xtc.playbook.scenarios"))
                    .parent.parent.parent
                    .resolve("scripts/xtc-lsp")
            val project = with(driver) { singleProject().getBasePath() }
            val restart =
                ProcessBuilder("python3", script.toString(), "restart", "--project", project, "--ide", "intellij")
                    .redirectErrorStream(true)
                    .start()
            try {
                check(restart.waitFor(HANG_SECONDS, SECONDS)) { "CLI restart timed out" }
                val output = restart.inputStream.bufferedReader().readText()
                check(restart.exitValue() == 0) { output }
            } finally {
                if (restart.isAlive) restart.destroyForcibly()
            }
        }

        fun holdReferences(file: String?) = with(driver) { utility(ProgressUi::class).holdReferences(file) }

        fun workload(methods: Int) =
            "module ${data.string("module")} {\n    static Int value = 1;\n" +
                (0 until methods).joinToString("\n") {
                    "    Int read$it() { return value; }"
                } +
                "\n}\n"

        val large = workload(data["methods"].asInt)

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
                awaitUi("real compiler reference progress", HANG_LIMIT) {
                    check(!pending.isDone()) { "Workload completed before progress was exercised" }
                    visible()
                }
                withContext(OnDispatcher.EDT) { utility(ProgressUi::class).show(singleProject()) }
            }
            return pending
        }
        try {
            // the reference searches in this file wait until they are canceled, so that the case acts on work that has
            // not finished however fast the machine is: the server it restarts holds them
            holdReferences(data.string("file"))
            val held = requireNotNull(protocol.server().getCurrentProcessId())
            restart()
            with(driver) {
                awaitUi("the server that holds the searches starts", HANG_LIMIT) {
                    protocol.server().getCurrentProcessId() != held && !utility(ProgressUi::class).alive(held)
                }
            }
            val canceled = start(large)
            with(driver) {
                awaitUi("visible progress Cancel control", HANG_LIMIT) {
                    check(!canceled.isDone()) { "Workload completed before Cancel was exercised" }
                    // False means the button is not visible yet; a successful click ends polling.
                    withContext(OnDispatcher.EDT) {
                        utility(ProgressUi::class).cancel(singleProject(), title)
                    }
                }
            }
            val outcome =
                runCatching { protocol.await("textDocument/references", canceled) }
                    .onFailure { if (it !is CancellationException && it.mustStopPlaybook()) throw it }
            check(
                outcome.exceptionOrNull().let {
                    it is CancellationException || (it is ClientRequestFailure && it.code == ResponseErrorCode.RequestCancelled.value)
                },
            ) {
                "Cancel must terminate the pending request: ${outcome.exceptionOrNull() ?: "completed before cancellation"}"
            }
            with(driver) { awaitUi("canceled progress disappears", HANG_LIMIT) { !visible() } }
            check(!query("textDocument/hover", document, large.indexOf("value")).isJsonNull)
            clean(document)
            val wrapper = protocol.server()
            val previous = requireNotNull(wrapper.getCurrentProcessId())
            val pending = start(large + "// unsaved restart\n")
            // The CLI must reach the editor, then reuse its normal restart and unsaved-buffer replay. The server it
            // starts holds no search, as the rest of the run's do not.
            holdReferences(null)
            restart()
            with(driver) {
                awaitUi("old process exits and pending request retires", HANG_LIMIT) {
                    pending.isDone() && !utility(ProgressUi::class).alive(previous)
                }
            }
            check(protocol.server().getCurrentProcessId() != previous)
            check(document.text == large + "// unsaved restart\n")
            replace(document, data.string("source"))
            clean(document)
            with(driver) { awaitUi("retired progress disappears", HANG_LIMIT) { !visible() } }
        } finally {
            holdReferences(null)
            with(driver) {
                withContext(OnDispatcher.EDT) { utility(ProgressUi::class).hide(singleProject()) }
            }
        }
    }
}

/** Observes and cancels the IDE's displayed work-progress indicators. */
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

    fun holdReferences(file: String?)
}
