package org.xtclang.idea.lsp

import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
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

/** Format asynchronously with version-checked, undoable edits, including closed tabs. */
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
        val stamp = document.modificationStamp
        val application = ApplicationManager.getApplication()
        val synchronous = document.getUserData(FORMAT_DOCUMENT_SYNCHRONOUSLY) == true || application.isHeadlessEnvironment
        val modality = ModalityState.defaultModalityState()
        val range =
            request.formattingRanges.firstOrNull()?.takeUnless { it.length == original.length }
        val support = LSPFormattingSupport(file)
        val cancelled = AtomicBoolean()

        fun current() =
            !cancelled.get() && !file.project.isDisposed && source.isValid &&
                document.modificationStamp == stamp && document.text == original
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
                    if (!ReadAction.computeBlocking<Boolean, RuntimeException>(::current)) {
                        request.onTextReady(null)
                    } else {
                        // LSP ranges belong to the request, never to a concurrently edited buffer.
                        val snapshot = EditorFactory.getInstance().createDocument(original)
                        val formatted = LSPIJUtils.applyEdits(snapshot, edits ?: emptyList())
                        when {
                            formatted == original -> {
                                request.onTextReady(null)
                            }

                            synchronous -> {
                                request.onTextReady(formatted)
                            }

                            else -> {
                                // TODO LSP4IJ: UP24 — IDEA's asynchronous formatting application is
                                // undo-transparent: standalone Format undoes but cannot redo. Keep
                                // synchronous Save's command; give asynchronous replies their own
                                // short write command without waiting for the server on the EDT.
                                application.invokeLater({
                                    try {
                                        WriteCommandAction
                                            .writeCommandAction(file.project)
                                            .withName("Format Ecstasy")
                                            .run<RuntimeException> {
                                                if (current()) document.setText(formatted)
                                            }
                                        request.onTextReady(null)
                                    } catch (failure: RuntimeException) {
                                        request.onError("Ecstasy formatting error", failure.message ?: "Formatting failed")
                                    }
                                }, modality)
                            }
                        }
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
