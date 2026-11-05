package org.xtclang.idea

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

    companion object {
        private const val SOURCE =
            "module M {\n    class Tally {}\n    void run(Tally[] Tallies) {\n        <caret>\n    }\n}\n"
    }
}
