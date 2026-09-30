package org.xtclang.idea.lsp

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vfs.VirtualFile
import com.redhat.devtools.lsp4ij.LSPIJUtils
import java.net.URI
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either

/** Preflight every target, then check the same document incarnations inside one undo command. */
internal class ServerWorkspaceEdit
private constructor(
    private val features: XtcClientFeatures,
    private val ownership: DocumentStartupMessages,
    private val server: Any,
    private val label: String,
    private val targets: List<Target>,
) {
    private data class Replacement(val start: Int, val end: Int, val text: String)

    private data class Target(
        val uri: String,
        val version: Int?,
        val file: VirtualFile,
        val document: Document,
        val stamp: Long,
        val snapshot: DocumentStartupMessages.Snapshot?,
        val replacements: List<Replacement>,
    ) {
        fun current(features: XtcClientFeatures, ownership: DocumentStartupMessages): Boolean =
            file.isValid &&
                features.getFileUri(file) == URI(uri) &&
                file.isWritable &&
                document.isWritable &&
                document.modificationStamp == stamp &&
                (if (snapshot == null)
                    features.serverWrapper.getOpenedDocument(features.getFileUri(file)) == null
                else ownership.isCurrent(uri, version, snapshot))
    }

    fun apply(): ApplyWorkspaceEditResponse =
        WriteCommandAction.writeCommandAction(features.project)
            .withName(label)
            .withGlobalUndo()
            .compute<ApplyWorkspaceEditResponse, RuntimeException> {
                if (
                    features.project.isDisposed ||
                        features.documents.get() !== ownership ||
                        features.languageServer !== server ||
                        !targets.all { it.current(features, ownership) }
                ) {
                    refused("The connection or an edited document changed before application")
                } else {
                    targets.forEach { target ->
                        target.replacements.asReversed().forEach {
                            target.document.replaceString(it.start, it.end, it.text)
                        }
                    }
                    ApplyWorkspaceEditResponse(true)
                }
            }

    companion object {
        fun prepare(
            features: XtcClientFeatures,
            params: ApplyWorkspaceEditParams,
        ): ServerWorkspaceEdit {
            val ownership =
                requireNotNull(features.documents.get()) { "Connection is not initialized" }
            val server = requireNotNull(features.languageServer) { "Connection is not running" }
            val edit = params.edit
            require(edit.changeAnnotations.orEmpty().values.none { it.needsConfirmation == true }) {
                "This edit requires confirmation; use the editor's refactoring action"
            }
            val changes = edit.documentChanges
            val requests =
                if (changes != null)
                    changes.map { change ->
                        require(change.isLeft) {
                            "Use the guarded Ecstasy Rename/Move action for resource operations"
                        }
                        val document = change.left
                        Triple(
                            document.textDocument.uri,
                            document.textDocument.version,
                            textEdits(document.edits),
                        )
                    }
                else edit.changes.orEmpty().map { (uri, edits) -> Triple(uri, null, edits) }
            require(requests.map { URI(it.first) }.distinct().size == requests.size) {
                "Duplicate document edits"
            }
            val targets = requests.map { (uri, version, edits) ->
                val file =
                    requireNotNull(features.findFileByUri(uri)) {
                        "Edit target is unavailable: $uri"
                    }
                require(file.isValid && file.isWritable) { "Edit target is read-only: $uri" }
                val document =
                    requireNotNull(LSPIJUtils.getDocument(file)) {
                        "Edit target has no document: $uri"
                    }
                require(document.isWritable) { "Edit target is read-only: $uri" }
                val opened = features.serverWrapper.getOpenedDocument(features.getFileUri(file))
                val snapshot = ownership.editSnapshot(uri, version)
                require(if (opened != null) snapshot != null else version == null) {
                    "Edit version cannot be verified for this document incarnation: $uri"
                }
                Target(
                    uri,
                    version,
                    file,
                    document,
                    document.modificationStamp,
                    snapshot,
                    replacements(document, edits),
                )
            }
            return ServerWorkspaceEdit(
                features,
                ownership,
                server,
                params.label ?: "Ecstasy workspace edit",
                targets,
            )
        }

        private fun replacements(document: Document, edits: List<TextEdit>): List<Replacement> {
            fun offset(at: Position): Int {
                require(at.line in 0 until document.lineCount && at.character >= 0) {
                    "Invalid edit position"
                }
                val start = document.getLineStartOffset(at.line)
                require(at.character <= document.getLineEndOffset(at.line) - start) {
                    "Edit exceeds line bounds"
                }
                val offset = start + at.character
                val text = document.immutableCharSequence
                require(
                    offset == 0 ||
                        offset == text.length ||
                        !Character.isSurrogatePair(text[offset - 1], text[offset])
                ) {
                    "Edit splits a UTF-16 surrogate pair"
                }
                return offset
            }
            val replacements =
                edits
                    .map {
                        Replacement(offset(it.range.start), offset(it.range.end), it.newText)
                            .also { edit ->
                                require(edit.start <= edit.end) { "Reversed edit range" }
                            }
                    }
                    .sortedWith(compareBy({ it.start }, { it.end }))
            require(
                replacements.zipWithNext().all { (a, b) -> a.end <= b.start && a.start != b.start }
            ) {
                "Overlapping edits"
            }
            return replacements
        }

        fun refused(reason: String) =
            ApplyWorkspaceEditResponse(false).apply { failureReason = reason }

        // IntelliJ's platform API declares List<TextEdit>; LSP4IJ 0.21 also supplies LSP4J 1.0's
        // List<Either<TextEdit, SnippetTextEdit>>. Its own compatibility adapter accepts both.
        // Normalize at this boundary without unchecked casts or replacing platform libraries.
        internal fun textEdits(edits: List<*>): List<TextEdit> = edits.map { edit ->
            requireNotNull(
                when (edit) {
                    is TextEdit -> edit
                    is Either<*, *> -> if (edit.isLeft) edit.left as? TextEdit else null
                    else -> null
                }
            ) {
                "Unsupported or malformed text edit (including snippet edits)"
            }
        }
    }
}
