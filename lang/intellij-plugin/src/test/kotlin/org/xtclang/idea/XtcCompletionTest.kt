package org.xtclang.idea

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.redhat.devtools.lsp4ij.DocumentContentSynchronizer
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.TimeUnit.SECONDS

/** Completion of a typed prefix in an Ecstasy file, with the language server's items or, without any, the file's words. */
class XtcCompletionTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor(StubLanguageServer(completions = listOf("ScoreCard", "Scoreboard", "size")))

    @Test
    fun `a typed prefix lists the server's items that it starts`() {
        editor.open("Completion.x", SOURCE) {
            type("Score")
            assertThat(completeBasic().map { it.lookupString }).containsExactlyInAnyOrder("ScoreCard", "Scoreboard")
        }
    }

    @Test
    fun `a typed prefix that the server completes with nothing lists the file's words`() {
        editor.open("Completion.x", SOURCE) {
            type("Tal")
            assertThat(completeBasic().map { it.lookupString }).contains("Tally", "Tallies")
        }
    }

    @Test
    fun `a prefix whose edit LSP4IJ still holds is the server's when the server is asked to complete it`() {
        val server = editor.server
        editor.open("Completion.x", SOURCE) { file ->
            holdingEdits(file) { type("Score") }
            assertThat(completeBasic().map { it.lookupString }).containsExactlyInAnyOrder("ScoreCard", "Scoreboard")
            assertThat(server.completionLines).containsExactly("        Score")
        }
    }

    /**
     * Runs [typing] with its edits held, as LSP4IJ holds an edit outside a test until IntelliJ commits the document.
     * Ctrl+Space commits it and then waits on the EDT for the first items, so the held edit went out after the request.
     */
    private fun CodeInsightTestFixture.holdingEdits(
        file: PsiFile,
        typing: CodeInsightTestFixture.() -> Unit,
    ) {
        // TODO LSP4IJ: UP73 — in a test LSP4IJ sends an edit at once, so the test holds it as LSP4IJ does in the IDE
        val server =
            LanguageServiceAccessor
                .getInstance(project)
                .getLanguageServers(file, { true }, { true })
                .get(HANG_SECONDS, SECONDS)
                .single()
                .serverWrapper
        val synchronizer = checkNotNull(server.getOpenedDocument(server.toUri(file.virtualFile))?.synchronizer)
        synchronizer.didOpenFuture.get(HANG_SECONDS, SECONDS)
        val document = editor.document
        document.removeDocumentListener(synchronizer)
        try {
            typing()
        } finally {
            // as LSP4IJ adds it
            document.addDocumentListener(synchronizer, synchronizer)
        }
        val held = DocumentContentSynchronizer::class.java.getDeclaredField("changeEvents").apply { isAccessible = true }

        @Suppress("UNCHECKED_CAST")
        val changes = held.get(synchronizer) as MutableList<TextDocumentContentChangeEvent>
        synchronized(changes) { changes += TextDocumentContentChangeEvent(document.text) }
    }

    companion object {
        private const val SOURCE =
            "module M {\n    class Tally {}\n    void run(Tally[] Tallies) {\n        <caret>\n    }\n}\n"
    }
}
