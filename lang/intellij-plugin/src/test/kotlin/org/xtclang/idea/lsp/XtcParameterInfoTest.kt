package org.xtclang.idea.lsp

import com.intellij.codeInsight.hint.ParameterInfoControllerBase
import com.intellij.lang.parameterInfo.LanguageParameterInfo
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.ParameterInformation
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureInformation
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.xtclang.idea.HANG_SECONDS
import org.xtclang.idea.LightEditor
import org.xtclang.idea.StubLanguageServer
import org.xtclang.idea.XtcIntelliJLanguage
import org.xtclang.idea.holdingEdits
import java.util.concurrent.TimeUnit

/** Parameter info pops up as a call's `(` is typed in an Ecstasy file, with the language server's signature. */
class XtcParameterInfoTest {
    @JvmField
    @RegisterExtension
    val editor =
        LightEditor(
            StubLanguageServer(
                signature =
                    SignatureHelp(
                        listOf(SignatureInformation("Int[] deal(Int numCards)", "", listOf(ParameterInformation("Int numCards")))),
                        0,
                        0,
                    ),
            ),
        )

    @Test
    fun `a typed call's parenthesis shows the signature`() {
        // without a handler for the file's language nothing would ever show, which the wait below cannot tell
        assertThat(LanguageParameterInfo.INSTANCE.allForLanguage(XtcIntelliJLanguage)).hasAtLeastOneElementOfType(
            XtcParameterInfoHandler::class.java,
        )
        editor.open("Calls.x", "module M {\n    void run(Deck deck) {\n        <caret>\n    }\n}\n") { }
        val tester = CompletionAutoPopupTester(editor.fixture)
        tester.runWithAutoPopupEnabled {
            tester.typeWithPauses("deck.deal(")
            runInEdtAndWait { PsiDocumentManager.getInstance(editor.fixture.project).commitAllDocuments() }
            // the popup that "(" schedules asks the server, whose answer comes on its own thread
            val hung = System.nanoTime() + TimeUnit.SECONDS.toNanos(HANG_SECONDS)
            while (!runInEdtAndGet { ParameterInfoControllerBase.existsForEditor(editor.fixture.editor) } && System.nanoTime() < hung) {
                runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() }
                Thread.sleep(10)
            }
        }
        assertThat(editor.server.requests).contains("textDocument/signatureHelp")
        assertThat(runInEdtAndGet { ParameterInfoControllerBase.existsForEditor(editor.fixture.editor) }).isTrue()
    }

    @Test
    fun `a call's parenthesis whose edit LSP4IJ still holds is the server's when it is asked for the signature`() {
        val server = editor.server
        editor.open("Calls.x", "module M {\n    void run(Deck deck) {\n        <caret>\n    }\n}\n") { file ->
            holdingEdits(file) { type("deck.deal(") }
            performEditorAction(IdeActions.ACTION_EDITOR_SHOW_PARAMETER_INFO)
        }
        // the request goes from a pooled thread
        val hung = System.nanoTime() + TimeUnit.SECONDS.toNanos(HANG_SECONDS)
        while (server.signatureLines.isEmpty() && System.nanoTime() < hung) {
            runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() }
            Thread.sleep(10)
        }
        assertThat(server.signatureLines.firstOrNull()).isEqualTo("        deck.deal(")
    }
}
