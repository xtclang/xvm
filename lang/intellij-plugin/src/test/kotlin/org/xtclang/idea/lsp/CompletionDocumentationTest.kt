package org.xtclang.idea.lsp

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.platform.backend.documentation.impl.computeDocumentationBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.xtclang.idea.LightEditor
import org.xtclang.idea.StubLanguageServer

/**
 * Quick Documentation (Ctrl+Q) on an Ecstasy completion item shows the documentation that the language server gives the
 * item only as it resolves it, as the real server does. The item is resolved only then: listing the items and selecting
 * one resolves none of them.
 */
class CompletionDocumentationTest {
    @JvmField
    @RegisterExtension
    val editor =
        LightEditor(
            StubLanguageServer(
                completions = listOf("ScoreCard", "Scoreboard"),
                completionDocumentation = mapOf("ScoreCard" to "The score of one card.", "Scoreboard" to "Every player's score."),
            ),
        )

    @Test
    fun `Quick Documentation on the selected completion item shows the documentation that its resolve gives`() {
        val server = editor.server
        val targets =
            editor.open("Documentation.x", SOURCE) { file ->
                type("Score")
                completeBasic()
                val lookup = checkNotNull(lookup) { "No completion list" }
                val item = lookup.items.single { it.lookupString == "ScoreCard" }
                lookup.currentItem = item
                // as Ctrl+Q asks for the documentation of the list's current item
                IdeDocumentationTargetProvider.getInstance(project).documentationTargets(this.editor, file, item)
            }
        assertThat(server.resolvedCompletions).isEmpty()
        val documentation = targets.mapNotNull { computeDocumentationBlocking(it.createPointer())?.html }
        assertThat(documentation)
            .describedAs("the documentation of the targets %s", targets.map { it.javaClass.name })
            .singleElement()
            .asString()
            .contains("The score of one card.")
        assertThat(server.resolvedCompletions).containsExactly("ScoreCard")
    }

    companion object {
        private const val SOURCE = "module M {\n    void run() {\n        <caret>\n    }\n}\n"
    }
}
