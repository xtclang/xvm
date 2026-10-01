package org.xtclang.idea.lsp

import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.client.features.LSPFormattingFeature
import com.redhat.devtools.lsp4ij.features.formatting.LSPFormattingAndRangeBothService
import com.redhat.devtools.lsp4ij.features.formatting.LSPFormattingParams
import com.redhat.devtools.lsp4ij.features.formatting.LSPFormattingSupport
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** Keep the IDE's asynchronous edit/Undo handling, including Save All for closed tabs. */
class XtcFormattingService : LSPFormattingAndRangeBothService() {
    override fun canSupportFormatting(
        feature: LSPFormattingFeature,
        file: PsiFile,
    ): Boolean =
        file.virtualFile?.extension == "x" &&
            feature.clientFeatures.isServerDefinition(CompilerSettings.SERVER_ID) &&
            super.canSupportFormatting(feature, file)

    // TODO LSP4IJ: UP12 — LSPFormattingSupport.format accepts a nullable editor but dereferences it
    // after the reply. Apply edits to the captured document until upstream fixes that path.
    override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask? {
        val input = request.ioFile ?: return null
        val source = LSPIJUtils.findResourceFor(input) ?: return null
        val file = LSPIJUtils.getPsiFile(source, request.context.project) ?: return null
        val document = LSPIJUtils.getDocument(source) ?: return null
        val original = request.documentText
        val range =
            request.formattingRanges.firstOrNull()?.takeUnless { it.length == original.length }
        val support = LSPFormattingSupport(file)
        val cancelled = AtomicBoolean()
        return object : FormattingTask {
            override fun run() {
                try {
                    if (cancelled.get()) return
                    val servers =
                        LanguageServiceAccessor
                            .getInstance(file.project)
                            .getLanguageServers(
                                file,
                                {
                                    it.isServerDefinition(CompilerSettings.SERVER_ID) &&
                                        it.formattingFeature.isEnabled(file)
                                },
                                { it.formattingFeature.isFormattingSupported(file) },
                            )
                    val server =
                        ProgressIndicatorUtils.awaitWithCheckCanceled(servers).singleOrNull()
                    if (server == null || cancelled.get()) {
                        request.onTextReady(null)
                        return
                    }
                    val options =
                        server.clientFeatures.formattingFeature.getFormattingOptions(file, null)
                    val result =
                        support.getFeatureData(
                            LSPFormattingParams(range, document, server, options),
                        )
                    if (cancelled.get()) support.cancel()
                    val edits = result?.let { ProgressIndicatorUtils.awaitWithCheckCanceled(it) }
                    val current =
                        ReadAction.computeBlocking<Boolean, RuntimeException> {
                            !file.project.isDisposed && source.isValid && document.text == original
                        }
                    if (cancelled.get() || !current) {
                        request.onTextReady(null)
                    } else {
                        // LSP ranges belong to the request, never to a concurrently edited buffer.
                        val snapshot = EditorFactory.getInstance().createDocument(original)
                        request.onTextReady(LSPIJUtils.applyEdits(snapshot, edits ?: emptyList()))
                    }
                } catch (cancelled: ProcessCanceledException) {
                    support.cancel()
                    throw cancelled
                } catch (_: CancellationException) {
                    request.onTextReady(null)
                } catch (failure: RuntimeException) {
                    request.onError(
                        "Ecstasy formatting error",
                        failure.cause?.message ?: failure.message ?: "Formatting failed",
                    )
                }
            }

            override fun cancel(): Boolean {
                cancelled.set(true)
                support.cancel()
                return true
            }

            override fun isRunUnderProgress(): Boolean = true
        }
    }
}
