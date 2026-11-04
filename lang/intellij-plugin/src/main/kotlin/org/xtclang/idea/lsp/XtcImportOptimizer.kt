package org.xtclang.idea.lsp

import com.intellij.lang.ImportOptimizer
import com.intellij.lang.SuspendableImportOptimizer
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import kotlinx.coroutines.future.await
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionContext
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeActionTriggerKind
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.xtclang.idea.XtcPsiFile
import java.net.URI
import java.util.concurrent.CancellationException

/**
 * Code | Optimize Imports in an Ecstasy file: runs the language server's Organize imports, the
 * `source.organizeImports` code action, which removes the imports nothing uses and sorts the rest.
 *
 * It is a SuspendableImportOptimizer, which IntelliJ marks experimental, because Optimize Imports calls a plain
 * optimizer's processFile inside a read action, where waiting for the server would hold every write back; it calls
 * processFileSuspend outside one. Its runnable applies the edit in the write command that Optimize Imports runs it
 * in, one Undo, and only when the document is still the one the server answered for.
 */
class XtcImportOptimizer : SuspendableImportOptimizer {
    override fun supports(file: PsiFile): Boolean = file is XtcPsiFile

    override suspend fun processFileSuspend(file: PsiFile): Runnable {
        val source = file.virtualFile ?: return EmptyRunnable.getInstance()
        // the server's answer belongs to this stamp, which the runnable checks before it applies the answer
        val (document, stamp) =
            readAction {
                FileDocumentManager.getInstance().getDocument(source)?.let { it to it.modificationStamp }
            } ?: return EmptyRunnable.getInstance()
        return try {
            val server =
                LanguageServiceAccessor
                    .getInstance(file.project)
                    .getLanguageServers(
                        file,
                        {
                            it.isServerDefinition(CompilerSettings.SERVER_ID) &&
                                it.isEnabled(source) &&
                                it.codeActionFeature.isEnabled(file)
                        },
                        { it.codeActionFeature.isCodeActionSupported(file) },
                    ).await()
                    .singleOrNull() ?: return Report(NO_SERVER)
            val wrapper = server.serverWrapper
            val synchronizer = wrapper.getOpenedDocument(wrapper.toUri(source))?.synchronizer ?: return Report(NO_SERVER)
            synchronizer.didOpenFuture.thenCompose { synchronizer.flushPendingChanges() }.await()
            val uri = wrapper.toUriString(source)
            val action = action(server.textDocumentService.codeAction(params(uri)).await()) ?: return EmptyRunnable.getInstance()
            // a lazy action's edit comes when it is resolved
            val edit =
                action.edit
                    ?: action.data?.let {
                        server.textDocumentService
                            .resolveCodeAction(action)
                            .await()
                            .edit
                    }
                    ?: return EmptyRunnable.getInstance()
            Organize(document, stamp, edits(edit, uri))
        } catch (failure: RuntimeException) {
            if (failure is CancellationException || failure is ProcessCanceledException) throw failure
            Report("Optimize Imports failed: ${failure.message ?: failure.javaClass.simpleName}")
        }
    }

    /** Applies [edits] to [document] if it is still at [stamp], and tells the hint after Optimize Imports what it did. */
    internal class Organize(
        private val document: Document,
        private val stamp: Long,
        private val edits: List<TextEdit>,
    ) : ImportOptimizer.CollectingInfoRunnable {
        private var info: String? = null

        override fun run() {
            info =
                if (document.modificationStamp != stamp) {
                    "The file changed while the language server organized its imports; run Optimize Imports again."
                } else {
                    try {
                        ServerWorkspaceEdit.replacements(document, edits).asReversed().forEach {
                            document.replaceString(it.start, it.end, it.text)
                        }
                        OPTIMIZED
                    } catch (malformed: IllegalArgumentException) {
                        "The language server's import edit does not fit the file: ${malformed.message}"
                    }
                }
        }

        override fun getUserNotificationInfo(): String? = info
    }

    /** Changes nothing, and tells the hint after Optimize Imports why. */
    private class Report(
        private val info: String,
    ) : ImportOptimizer.CollectingInfoRunnable {
        override fun run() = Unit

        override fun getUserNotificationInfo(): String = info
    }

    internal companion object {
        /** The text of IntelliJ's own hint after Optimize Imports changed a file. */
        const val OPTIMIZED = "Imports optimized"

        private const val NO_SERVER = "Optimize Imports needs the Ecstasy language server, which is not running for this file."

        /**
         * Asks for the document's `source.organizeImports` actions. Organize imports concerns the whole document, so
         * an empty range at its start serves; a range over the whole document would have the compiler prove every
         * range-bound refactoring in it, only for the server to leave them out by their kind.
         */
        fun params(uri: String): CodeActionParams =
            CodeActionParams(
                TextDocumentIdentifier(uri),
                Range(Position(0, 0), Position(0, 0)),
                CodeActionContext(emptyList(), listOf(CodeActionKind.SourceOrganizeImports)).apply {
                    triggerKind = CodeActionTriggerKind.Invoked
                },
            )

        /** The server's one organize-imports action among [results], or none when it offers none or several. */
        fun action(results: List<Either<Command, CodeAction>>?): CodeAction? =
            results
                .orEmpty()
                .mapNotNull { it.right }
                .filter {
                    it.kind == CodeActionKind.SourceOrganizeImports ||
                        it.kind?.startsWith("${CodeActionKind.SourceOrganizeImports}.") == true
                }.singleOrNull()

        /** The text edits of [edit], which must change only the document at [uri] and move or create no file. */
        fun edits(
            edit: WorkspaceEdit,
            uri: String,
        ): List<TextEdit> {
            val target = URI(uri)
            val changes = edit.documentChanges
            val documents =
                if (changes != null) {
                    changes.map { change ->
                        require(change.isLeft) { "it moves or creates a file" }
                        change.left.textDocument.uri to ServerWorkspaceEdit.textEdits(change.left.edits)
                    }
                } else {
                    edit.changes.orEmpty().map { (document, edits) -> document to edits }
                }
            require(documents.all { (document, _) -> URI(document) == target }) { "it changes another file" }
            return documents.flatMap { it.second }
        }
    }
}
