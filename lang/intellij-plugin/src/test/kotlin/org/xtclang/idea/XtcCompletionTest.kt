package org.xtclang.idea

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** Completion of a typed prefix in an Ecstasy file, with the language server's items or, without any, the file's words. */
class XtcCompletionTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor(StubLanguageServer(completions = listOf("ScoreCard", "Scoreboard", "size")))

    @Test
    fun `a typed prefix lists the server's items that it starts`() {
        editor.open("Completion.x", SOURCE) { file ->
            connected(file)
            type("Score")
            assertThat(completeBasic().map { it.lookupString }).containsExactlyInAnyOrder("ScoreCard", "Scoreboard")
        }
    }

    @Test
    fun `a typed prefix that the server completes with nothing lists the file's words`() {
        editor.open("Completion.x", SOURCE) { file ->
            connected(file)
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

    /** Waits until LSP4IJ has connected [file] to the server, before a test types in it. */
    private fun CodeInsightTestFixture.connected(file: PsiFile) {
        // TODO LSP4IJ: UP83 — LSP4IJ 0.30.1 reads the text it opens a document with as it asks for the file's servers,
        //  and adds the document's listener only once the server has initialized: what is typed in between never
        //  reaches the server, which then completes the line as it was opened (a full run of the unit tests, 2026-11-25);
        //  the product's OpenedTextRepair mends that, but the stub server's definition does not start it
        synchronizer(file)
    }

    companion object {
        private const val SOURCE =
            "module M {\n    class Tally {}\n    void run(Tally[] Tallies) {\n        <caret>\n    }\n}\n"
    }
}
