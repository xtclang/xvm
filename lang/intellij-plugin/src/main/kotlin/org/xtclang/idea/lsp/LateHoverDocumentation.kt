package org.xtclang.idea.lsp

import com.intellij.model.Pointer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbService
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.LSPFileSupport
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.features.documentation.LSPDocumentationTarget
import com.redhat.devtools.lsp4ij.features.documentation.LSPDocumentationTargetProvider
import com.redhat.devtools.lsp4ij.features.documentation.LSPHoverParams
import kotlinx.coroutines.future.await
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.xtclang.idea.XtcFileType
import java.util.concurrent.CompletableFuture

/**
 * Quick Documentation in an Ecstasy file shows the language server's hover once it comes, however long it takes.
 *
 * TODO LSP4IJ: UP82 — LSP4IJ 0.30.1 waits 500 ms for the hover in a file of a language file type, and shows nothing
 *  when it comes later (redhat-developer/lsp4ij#2129, changed by our #2130 for 0.30.2). This provider runs after
 *  LSP4IJ's, and takes the hover request that LSP4IJ's made: when it has its answer, LSP4IJ's targets show it, and this
 *  provider gives none; when it hasn't, this provider's target shows it once it comes, as LSP4IJ 0.30.2 does for an
 *  Ecstasy file. IntelliJ shows the targets of every provider, so the documentation shows once. LSP4IJ counts a request
 *  as outdated once any file of the project has changed since it was made, as often right after a restart; the target
 *  then asks again, as LSP4IJ's next Quick Documentation would, which cancels the outdated request.
 */
class LateHoverDocumentation : DocumentationTargetProvider {
    override fun documentationTargets(
        file: PsiFile,
        offset: Int,
    ): List<DocumentationTarget> {
        if (file.fileType != XtcFileType || DumbService.isDumb(file.project)) return emptyList()
        val document = LSPIJUtils.getFile(file)?.let(LSPIJUtils::getDocument) ?: return emptyList()
        // the request of LSP4IJ's provider, which ran first, or a new one once LSP4IJ counts that as outdated
        val hover = lspHover(file) ?: newHover(file, offset, document)
        if (hover.isDone) return emptyList()
        logger<LateHoverDocumentation>().info("Quick Documentation of ${file.name} waits for a hover that LSP4IJ gave up on (UP82)")
        return listOf(LateHover(file, offset, document, hover))
    }

    /** The hover at [offset] of [file], shown once [hover] has it, if [document] has not changed since. */
    private class LateHover(
        private val file: PsiFile,
        private val offset: Int,
        private val document: Document,
        private val hover: CompletableFuture<*>,
    ) : DocumentationTarget {
        private val stamp = document.modificationStamp

        // as for a hover without a range: the answer that could have one is not there yet
        override fun computePresentation() = TargetPresentation.builder("").presentation()

        override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)

        // IntelliJ computes the documentation in a coroutine, which waits for the answer without holding a thread
        override fun computeDocumentation(): DocumentationResult =
            DocumentationResult.asyncDocumentation {
                var request = hover
                var html: String? = null
                while (true) {
                    // a stage of its own: await cancels what it waits for when IntelliJ cancels the coroutine, and the
                    // request is LSP4IJ's, whose cancellation the server would answer with an error
                    request.handle { _, _ -> }.await()
                    // LSP4IJ's targets come at once while the answered request is still its own; once LSP4IJ counts it as
                    // outdated, the new request is waited for, outside the read action
                    request = readAction {
                        when {
                            !file.isValid || document.modificationStamp != stamp -> null

                            lspHover(file) === request -> {
                                html =
                                    LSPDocumentationTargetProvider()
                                        .getHoverTargets(file, offset)
                                        .mapNotNull(LSPDocumentationTarget::getHtml)
                                        .takeIf { it.isNotEmpty() }
                                        ?.joinToString(SEPARATOR)
                                null
                            }

                            else -> newHover(file, offset, document)
                        }
                    } ?: break
                }
                html?.let(DocumentationResult::documentation)
            }
    }

    private companion object {
        // LSP4IJ's separator of the contents of one hover
        const val SEPARATOR = "<hr />"

        /**
         * The hover request that LSP4IJ holds for [file] while it counts it, without asking for one. Its answer type,
         * LSP4IJ's HoverData, is not visible here, and the provider only waits for the answer.
         */
        @Suppress("INFERRED_INVISIBLE_RETURN_TYPE_WARNING")
        fun lspHover(file: PsiFile): CompletableFuture<*>? = LSPFileSupport.getSupport(file).hoverSupport.validLSPFuture

        /** A new hover request at [offset], which LSP4IJ then holds, and which cancels the one it held. */
        @Suppress("INFERRED_INVISIBLE_RETURN_TYPE_WARNING")
        fun newHover(
            file: PsiFile,
            offset: Int,
            document: Document,
        ): CompletableFuture<*> =
            LSPFileSupport
                .getSupport(file)
                .hoverSupport
                .getHover(LSPHoverParams(TextDocumentIdentifier(), LSPIJUtils.toPosition(offset, document), offset))
    }
}
