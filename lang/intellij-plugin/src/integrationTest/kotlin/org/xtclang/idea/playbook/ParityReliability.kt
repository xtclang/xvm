package org.xtclang.idea.playbook

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.singleProject
import kotlin.time.Duration.Companion.seconds

internal fun ParityScenarios.reliabilityCases() {
    case("X146") { data ->
        write(data.string("library"), data.string("original"))
        write(data.string("consumer"), data.string("source"))
        configure(
            listOf(
                SharedScenarios.SourceModule(
                    data.string("libraryModule"),
                    uri(data.string("library")),
                    emptyList(),
                ),
                SharedScenarios.SourceModule(
                    data.string("consumerModule"),
                    uri(data.string("consumer")),
                    listOf(data.string("libraryModule")),
                ),
            ),
        )
        val library = open(data.string("library"))
        val consumer = open(data.string("consumer"))
        val version = version(consumer)
        val support = with(driver) { semanticSupport(consumer.editor).getInlayHintsSupport() }
        val events = with(driver) { utility(RefreshRequests::class).listen(singleProject()) }

        fun verify(expected: String) {
            // Read the native provider's automatically refreshed future, without manually
            // requesting inlays or editing the consumer to trigger another analysis.
            with(driver) {
                awaitUi("untouched consumer receives $expected inlay", 45.seconds) {
                    support
                        .getValidLSPFuture()
                        ?.takeIf { it.isDone() && !it.isCompletedExceptionally() }
                        ?.get()
                        ?.any { item ->
                            protocol
                                .copy(item.inlayHint())
                                .asJsonObject["label"]
                                .toString()
                                .contains(expected)
                        } == true
                }
            }
            check(consumer.text == data.string("source"))
            check(version(consumer) == version)
        }
        try {
            verify(data.string("before"))
            listOf("changed" to "after", "original" to "before").forEach { (source, expected) ->
                events.clear()
                replace(library, data.string(source))
                // Display the untouched consumer again; no source mutation or explicit inlay
                // request.
                consumer.editor
                with(driver) {
                    awaitUi("native dependency refresh requests", 45.seconds) {
                        events
                            .values()
                            .containsAll(
                                listOf(
                                    "workspace/inlayHint/refresh",
                                    "workspace/semanticTokens/refresh",
                                ),
                            )
                    }
                }
                verify(data.string(expected))
                clean(consumer)
            }
        } finally {
            events.dispose()
        }
    }
    case("X147") { data ->
        val document = open(data.string("file"), data.string("source"))
        clean(document)
        with(driver) {
            val settings = utility(LanguageServicePage::class).content(singleProject())
            val page =
                withContext(OnDispatcher.EDT) {
                    utility(CompilerReportPage::class).open(singleProject())
                }

            fun text() = withContext(OnDispatcher.EDT) { page.text() }

            fun complete(
                index: Int,
                name: String,
            ) {
                val barrier = withContext(OnDispatcher.EDT) { page.complete(index, name) }
                awaitUi("settings callback and EDT publication complete", 15.seconds) {
                    barrier.isDone()
                }
                check(!barrier.isCompletedExceptionally())
            }
            try {
                withContext(OnDispatcher.EDT) { page.reset() }
                complete(1, "NewestReport")
                check(text().contains("NewestReport"))
                complete(0, "RetiredReport")
                check(text().contains("NewestReport") && !text().contains("RetiredReport"))
                withContext(OnDispatcher.EDT) { page.reset() }
                val pendingPublication = text()
                val replyPid = protocol.server().getCurrentProcessId()
                val publication =
                    withContext(OnDispatcher.EDT) {
                        page.completeAndRestart(2, "RetiredBeforePublication")
                    }
                awaitUi("restart between report receipt and publication", 45.seconds) {
                    publication.isDone() &&
                        protocol.server().getCurrentProcessId()?.let { it != replyPid } == true
                }
                check(!publication.isCompletedExceptionally())
                check(text() == pendingPublication)
                withContext(OnDispatcher.EDT) { page.reset() }
                val before = text()
                val previousPid = protocol.server().getCurrentProcessId()
                withContext(OnDispatcher.EDT) {
                    utility(LanguageServicePage::class).transport(singleProject(), "incremental")
                }
                awaitUi("new connection owns settings", 45.seconds) {
                    protocol.server().getCurrentProcessId()?.let { it != previousPid } == true
                }
                complete(3, "OldSettings")
                check(text() == before)
                awaitUi("retired process exits", 45.seconds) {
                    previousPid == null ||
                        !ProcessHandle.of(previousPid).map { it.isAlive }.orElse(false)
                }
                withContext(OnDispatcher.EDT) { page.reset() }
                val disposed = text()
                withContext(OnDispatcher.EDT) { page.dispose() }
                complete(4, "DisposedReport")
                check(text() == disposed)
                check(document.text == data.string("source"))
            } finally {
                withContext(OnDispatcher.EDT) {
                    page.dispose()
                    utility(LanguageServicePage::class).restore(singleProject(), settings)
                }
            }
        }
    }
}

@Remote("org.xtclang.idea.playbook.probe.RefreshRequests", plugin = "org.xtclang.playbook.probe")
interface RefreshRequests {
    fun listen(project: Project): RefreshRequests

    fun values(): List<String>

    fun clear()

    fun dispose()
}

@Remote("org.xtclang.idea.playbook.probe.CompilerReportPage", plugin = "org.xtclang.playbook.probe")
interface CompilerReportPage {
    fun open(project: Project): CompilerReportPage

    fun reset()

    fun text(): String

    fun complete(
        index: Int,
        name: String,
    ): ClientFuture

    fun completeAndRestart(
        index: Int,
        name: String,
    ): ClientFuture

    fun dispose()
}
