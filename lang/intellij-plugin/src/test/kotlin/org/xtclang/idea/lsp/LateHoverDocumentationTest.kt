package org.xtclang.idea.lsp

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.impl.computeDocumentationAsync
import com.intellij.platform.backend.documentation.impl.computeDocumentationBlocking
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.redhat.devtools.lsp4ij.LSPFileSupport
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.features.documentation.LSPHoverParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.xtclang.idea.LightEditor
import org.xtclang.idea.StubLanguageServer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Quick Documentation (Ctrl+Q) in an Ecstasy file shows the server's hover however long the server takes to answer:
 * LSP4IJ 0.30.1 waits 500 ms for it (UP82), and LateHoverDocumentation shows a later answer once it comes. The
 * documentation shows once, whichever of the two has the answer.
 */
class LateHoverDocumentationTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor(StubLanguageServer(hover = true))

    @Test
    fun `Quick Documentation shows a hover that the server answers only after LSP4IJ stopped waiting`() {
        // the server answers when the test says, which is after LSP4IJ's provider has stopped waiting
        val answer = CompletableFuture<Hover?>()
        editor.server.hovers = { answer }
        val targets = editor.open("Late.x", SOURCE) { file -> documentationTargets(file) }
        assertThat(targets)
            .describedAs("the targets %s", targets.map { it.javaClass.name })
            .singleElement()
        answer.complete(hover())
        assertThat(documentation(targets)).singleElement().asString().contains(DOCUMENTATION)
    }

    @Test
    fun `Quick Documentation that IntelliJ cancels while it waits leaves the hover request to LSP4IJ`() {
        val answer = CompletableFuture<Hover?>()
        editor.server.hovers = { answer }
        val (targets, request) = editor.open("Late.x", SOURCE) { file -> documentationTargets(file) to hoverRequest(file) }
        val waiting = request.numberOfDependents
        runBlocking(Dispatchers.Default) {
            val computing = computeDocumentationAsync(targets.single().createPointer())
            // once the documentation waits for the request, an hour at most
            val start = System.nanoTime()
            while (request.numberOfDependents == waiting) {
                check(System.nanoTime() - start < HANG_NANOS) { "The documentation never waited for the hover" }
                delay(10)
            }
            // as IntelliJ cancels it, such as when the popup closes
            computing.cancelAndJoin()
        }
        assertThat(request.isCancelled).describedAs("LSP4IJ's hover request").isFalse()
        assertThat(answer.isCancelled).describedAs("the server's hover").isFalse()
        answer.complete(hover())
        assertThat(documentation(targets)).singleElement().asString().contains(DOCUMENTATION)
    }

    @Test
    fun `Quick Documentation shows a hover that the server answers at once only once`() {
        editor.server.hovers = { CompletableFuture.completedFuture(hover()) }
        val targets = editor.open("Late.x", SOURCE) { file -> documentationTargets(file) }
        assertThat(documentation(targets))
            .describedAs("the documentation of the targets %s", targets.map { it.javaClass.name })
            .singleElement()
            .asString()
            .contains(DOCUMENTATION)
    }

    private fun CodeInsightTestFixture.documentationTargets(file: PsiFile): List<DocumentationTarget> =
        IdeDocumentationTargetProvider.getInstance(project).documentationTargets(editor, file, editor.caretModel.offset)

    // LSP4IJ's request for the hover at the caret, which its provider and LateHoverDocumentation share; its answer
    // type, LSP4IJ's HoverData, is not visible here
    @Suppress("INFERRED_INVISIBLE_RETURN_TYPE_WARNING")
    private fun CodeInsightTestFixture.hoverRequest(file: PsiFile): CompletableFuture<*> {
        val offset = editor.caretModel.offset
        val params = LSPHoverParams(TextDocumentIdentifier(), LSPIJUtils.toPosition(offset, editor.document), offset)
        return LSPFileSupport.getSupport(file).hoverSupport.getHover(params)
    }

    private fun documentation(targets: List<DocumentationTarget>) =
        targets.mapNotNull { computeDocumentationBlocking(it.createPointer())?.html }

    private fun hover() = Hover(MarkupContent(MarkupKind.MARKDOWN, DOCUMENTATION))

    companion object {
        private val HANG_NANOS = TimeUnit.HOURS.toNanos(1)
        private const val DOCUMENTATION = "The deck of cards."
        private const val SOURCE = "module M {\n    void run() {\n        Int <caret>count = 0;\n    }\n}\n"
    }
}
