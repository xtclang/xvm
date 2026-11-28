package org.xtclang.idea.lsp

import com.intellij.codeStyle.AbstractConvertLineSeparatorsAction
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.client.features.LSPFormattingFeature
import com.redhat.devtools.lsp4ij.features.formatting.LSPFormattingAndRangeBothService
import com.redhat.devtools.lsp4ij.features.formatting.LSPFormattingParams
import com.redhat.devtools.lsp4ij.features.formatting.LSPFormattingSupport
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.services.LanguageServer
import org.xvm.lsp.protocol.XtcServerProtocol
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Format asynchronously with version-checked, undoable edits, including closed tabs, and give a whole-file format's file
 * the line separator that its `.editorconfig` asks for, which IntelliJ's EditorConfig support makes only new files' and
 * no text edit can give a document (`xtc/endOfLine`), for a file that File | File Properties | Line Separators converts:
 * Reformat Code gives it the separator and saves it, or converts it as that action does when the format changes no text,
 * and a synchronous format, on save or headless, sets the separator when a save writes the document after it, as it does
 * when the document has changes. A file whose separator, as IntelliJ detected it, is the one asked for keeps its line
 * endings, mixed ones too, as that action does.
 */
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
        // every range IntelliJ asks for, in one rangesFormatting; none formats the whole document
        val ranges =
            request.formattingRanges.takeUnless { ranges -> ranges.any { it.length == original.length } }.orEmpty()
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
                            LSPFormattingParams(ranges, document, server, options),
                        )
                    // a file that IntelliJ's own Line Separators action converts: local, writable, a text file it does not ignore
                    val convertible =
                        ranges.isEmpty() && source.isInLocalFileSystem && AbstractConvertLineSeparatorsAction.shouldProcess(source)
                    val ending = if (convertible) endOfLine(server.server, source) else null
                    if (cancelled.get()) support.cancel()
                    val edits = result?.let { ProgressIndicatorUtils.awaitWithCheckCanceled(it) }
                    // the separator to give the file, when it has another
                    val separator =
                        ending
                            ?.let { ProgressIndicatorUtils.awaitWithCheckCanceled(it) }
                            ?.let(LINE_SEPARATORS::get)
                            ?.takeIf { it != FileDocumentManager.getInstance().getLineSeparator(source, file.project) }
                    if (!ReadAction.computeBlocking<Boolean, RuntimeException>(::current)) {
                        request.onTextReady(null)
                    } else {
                        // LSP ranges belong to the request, never to a concurrently edited buffer.
                        val snapshot = EditorFactory.getInstance().createDocument(original)
                        val formatted = LSPIJUtils.applyEdits(snapshot, edits ?: emptyList())
                        when {
                            formatted == original && separator == null -> request.onTextReady(null)

                            synchronous -> {
                                // the save after it writes the file with the separator, which it does only when the
                                // document has changes, as the format's or the user's
                                separator
                                    ?.takeIf { formatted != original || FileDocumentManager.getInstance().isDocumentUnsaved(document) }
                                    ?.let { source.detectedLineSeparator = it }
                                request.onTextReady(formatted.takeIf { it != original })
                            }

                            else -> {
                                // TODO LSP4IJ: UP24 — IDEA's asynchronous formatting application is
                                // undo-transparent: standalone Format undoes but cannot redo. Keep
                                // synchronous Save's command; give asynchronous replies their own
                                // short write command without waiting for the server on the EDT.
                                application.invokeLater({
                                    try {
                                        // nothing on the EDT changes the document between the check and the edits
                                        if (current()) {
                                            if (formatted != original) {
                                                WriteCommandAction
                                                    .writeCommandAction(file.project)
                                                    .withName("Format Ecstasy")
                                                    .run<RuntimeException> { document.setText(formatted) }
                                            }
                                            separator?.let { lineSeparator(file.project, source, document, it, formatted != original) }
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

/**
 * Gives [file] of [project] the line [separator] after Reformat Code, on the EDT: after a format that [changed] the text of
 * [document], the file's separator for the save that writes the document, which keeps the format to undo, as a rewrite of
 * the file outside the save does not; after one that changed nothing, as File | File Properties | Line Separators does,
 * which saves the file and writes it again with them.
 */
private fun lineSeparator(
    project: Project,
    file: VirtualFile,
    document: Document,
    separator: String,
    changed: Boolean,
) {
    if (changed) {
        file.detectedLineSeparator = separator
        FileDocumentManager.getInstance().saveDocument(document)
    } else {
        AbstractConvertLineSeparatorsAction.changeLineSeparators(project, file, separator)
    }
}

/** The line separators by the names that `.editorconfig`'s `end_of_line` gives them, as `xtc/endOfLine` answers. */
private val LINE_SEPARATORS = mapOf("lf" to "\n", "crlf" to "\r\n", "cr" to "\r")

/**
 * The line ending that [file]'s `.editorconfig` files ask for, as [server] answers `xtc/endOfLine`: none when they ask
 * for none, or while IntelliJ's EditorConfig support is off, which the server is told. A failure leaves the file's
 * separator as it is, and the format goes on.
 */
private fun endOfLine(
    server: LanguageServer,
    file: VirtualFile,
): CompletableFuture<String?>? =
    (server as? XtcServerProtocol)
        ?.endOfLine(TextDocumentIdentifier(LSPIJUtils.toUriAsString(file)))
        ?.exceptionally { failure ->
            logger<XtcFormattingService>().warn("Could not read the line ending of ${file.path}", failure)
            null
        }
