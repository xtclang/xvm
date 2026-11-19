package org.xtclang.idea.dap

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import com.redhat.devtools.lsp4ij.LanguageServersRegistry
import com.redhat.devtools.lsp4ij.dap.DAPDebuggerEditorsProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.xtclang.idea.LightEditor
import org.xtclang.idea.XtcFileType
import org.xtclang.idea.XtcPsiFile

/**
 * An expression editor's document, such as the Debug window's expression field's, has a physical fragment, which
 * IntelliJ's daemon highlights, as LSP4IJ's debugger editors make it (#1763): an Ecstasy file, as LSP4IJ 0.30.0 makes
 * the fragment of a language with a parser (#2001). LSP4IJ 0.21.0's, never physical, left the daemon starting again
 * every 300 ms while a session was paused (UP37).
 */
class ExpressionFragmentsTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor()

    @Test
    fun `an expression editor's fragment is physical, which the daemon can highlight, and stays off the server`() {
        editor.open("Hand.x", "module Hand { void deal(Int that) { deal(th<caret>at); } }") { file ->
            val context = checkNotNull(file.findElementAt(caretOffset))
            val document =
                DAPDebuggerEditorsProvider(XtcFileType, null)
                    .createDocument(project, XExpressionImpl.fromText("that"), context, EvaluationMode.EXPRESSION)
            val fragment = checkNotNull(PsiDocumentManager.getInstance(project).getPsiFile(document))
            // the language's own file, as LSP4IJ 0.30.0 makes a fragment of a language that has a parser (#2001)
            assertThat(fragment).isInstanceOf(XtcPsiFile::class.java)
            assertThat(fragment.isPhysical).isTrue()
            assertThat(DaemonCodeAnalyzer.getInstance(project).isHighlightingAvailable(fragment)).isTrue()
            // a light file, which LSP4IJ does not connect to a language server, as it does not LSP4IJ's own fragment
            assertThat(fragment.virtualFile).isInstanceOf(LightVirtualFile::class.java)
            assertThat(LanguageServersRegistry.getInstance().isFileSupported(fragment)).isFalse()
        }
    }
}
