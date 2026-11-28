package org.xtclang.idea.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.LocalTimeCounter
import com.redhat.devtools.lsp4ij.DocumentContentSynchronizer
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.LanguageServerWrapper
import com.redhat.devtools.lsp4ij.OpenedDocument
import com.redhat.devtools.lsp4ij.internal.CancellationSupport
import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.RenameFile
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageServer
import org.xvm.lsp.protocol.RenameProposal
import org.xvm.lsp.protocol.XtcServerProtocol
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

/**
 * A rename response bound to the client documents that produced it. The XTC rename applies its proposal itself,
 * outside LSP4IJ's own checks, so it keeps the request's immutable snapshot until application and checks it under
 * the same write action as all of the returned edits, with the server's proof of the text of each document that the
 * edit changes, [proved] ([ProvedTexts]). No second version counter or long-lived document listener is needed.
 */
class XtcRenameEdit
    private constructor(
        private val snapshot: Snapshot,
        val edit: WorkspaceEdit,
        private val graph: SourceGraphEdit? = null,
        private val command: String = "Rename",
        /** The hash of the text that the server computed each document's changes against, by URI ([RenameProposal.textHashes]). */
        private val proved: Map<String, String> = emptyMap(),
    ) {
        /** Return false without changing any file when the request's documents have been retired. */
        fun apply(): Boolean {
            ApplicationManager.getApplication().assertIsDispatchThread()
            val project = snapshot.wrapper.project
            if (project.isDisposed) return false
            return WriteCommandAction
                .writeCommandAction(project)
                .withName(command)
                .withGlobalUndo()
                .withUndoConfirmationPolicy(undoConfirmation())
                .compute<Boolean, RuntimeException> {
                    val resourceMoves = edit.documentChanges.orEmpty().filter { it.isRight }
                    val targets =
                        resourceMoves
                            .mapNotNull {
                                (it.right as? RenameFile)?.let { move ->
                                    Path.of(URI(move.oldUri)) to Path.of(URI(move.newUri))
                                }
                            }.toMap()
                    val local = LocalFileSystem.getInstance()
                    val resolved =
                        targets
                            .mapNotNull { (from, to) ->
                                val file = local.findFileByNioFile(from) ?: return@mapNotNull null
                                val parent =
                                    local.findFileByNioFile(to.parent) ?: return@mapNotNull null
                                from to (file to parent)
                            }.toMap()
                    if (
                        !snapshot.isCurrent() ||
                        graph?.isCurrent() == false ||
                        resourceMoves.size != targets.size ||
                        resolved.size != targets.size ||
                        (targets.isNotEmpty() && !FileMoveTargets.valid(targets))
                    ) {
                        false
                    } else {
                        if (!ProvedTexts.current(edit, proved, snapshot.read())) return@compute false
                        graph?.beforeApply()
                        // TODO LSP4IJ: UP03 — 0.21 only renames the basename, ignoring a changed parent URI.
                        // Apply resource moves through VFS inside this same undo command.
                        // Scope the already approved physical operation so the VFS listener does
                        // not request another compiler edit while this write command holds the EDT.
                        val preflighted = (snapshot.wrapper.clientFeatures as XtcClientFeatures).preflightedRenames
                        edit.documentChanges.orEmpty().forEach { change ->
                            if (change.isRight && change.right is RenameFile) {
                                val move = change.right as RenameFile
                                val (file, parent) = resolved.getValue(Path.of(URI(move.oldUri)))
                                val target = Path.of(URI(move.newUri))
                                if (file.parent != parent) {
                                    preflighted.apply(Path.of(file.path), Path.of(parent.path, file.name)) {
                                        file.move(this, parent)
                                    }
                                }
                                if (file.name != target.fileName.toString()) {
                                    preflighted.apply(Path.of(file.path), target) {
                                        file.rename(this, target.fileName.toString())
                                    }
                                }
                            } else {
                                LSPIJUtils.applyWorkspaceEdit(WorkspaceEdit(listOf(change)))
                            }
                        }
                        if (edit.documentChanges == null) LSPIJUtils.applyWorkspaceEdit(edit)
                        graph?.apply()
                        true
                    }
                }
        }

        /**
         * The command's undo policy. IntelliJ's refactorings run with the DEFAULT policy, whose Undo asks first when the
         * command is global (BaseRefactoringProcessor), as this one always is: a rename or a move that changes more than
         * one file, or a file's path, asks, and one that changes one file's text undoes at once, as an in-place rename's
         * does.
         */
        private fun undoConfirmation(): UndoConfirmationPolicy =
            if (ServerWorkspaceEdit.globalUndo(edit)) {
                UndoConfirmationPolicy.DEFAULT
            } else {
                UndoConfirmationPolicy.DO_NOT_REQUEST_CONFIRMATION
            }

        /** Open buffer and synchronizer identity captured before requesting a rename, and whether its file existed then. */
        private data class Buffer(
            val opened: OpenedDocument,
            val synchronizer: DocumentContentSynchronizer,
            val stamp: Long,
            val url: String,
            val valid: Boolean,
        ) {
            // TODO LSP4IJ: UP93 — LSP4IJ keeps a document connected that no editor shows once its file is deleted, with no
            //  didClose, as X337's Gradle debug left GradleDebugged.x in the IntelliJ playbook's full catalog: a file deleted
            //  before the request has changed nothing since, and stays current while no file has taken its place, which
            //  LSP4IJ would connect to the stale document whose deleted text the server keeps. Require a valid file again
            //  once a release we ship closes such a document
            fun isCurrent(): Boolean =
                (if (valid) opened.file.isValid else stillDeleted()) &&
                    opened.file.url == url &&
                    opened.synchronizer === synchronizer &&
                    synchronizer.document.modificationStamp == stamp

            private fun stillDeleted(): Boolean = !opened.file.isValid && VirtualFileManager.getInstance().findFileByUrl(url) == null
        }

        /** Source file, original modification stamp and proposed destination for a rename. */
        private data class FileMove(
            val file: VirtualFile,
            val url: String,
            val stamp: Long,
            val target: Path,
        ) {
            fun isCurrent(): Boolean =
                file.isValid &&
                    file.url == url &&
                    file.modificationStamp == stamp &&
                    FileMoveTargets.valid(mapOf(Path.of(file.path) to target))
        }

        /**
         * Connection, buffers and file moves that must remain current while the server proves the rename, which [asked]
         * at that modification count, and the documents with unsaved changes that the server did not read then, [unread]:
         * it proved the rename against their files on the disk. A document opened since whose text is its file's as the
         * request was asked, as a library's read-only source or a file that an editor or a request opens, changes no
         * source that the proof read, and leaves it current.
         */
        private data class Snapshot(
            val wrapper: LanguageServerWrapper,
            val server: LanguageServer,
            val buffers: List<Buffer>,
            val asked: Long,
            val unread: Set<Document>,
            val moves: List<FileMove> = emptyList(),
        ) {
            /** The documents whose buffers the server read for the request, as the snapshot captured them. */
            fun read(): Set<Document> = buffers.mapTo(hashSetOf()) { it.synchronizer.document }

            fun isCurrent(): Boolean {
                val opened = wrapper.openedDocuments.toSet()
                val captured = buffers.map { it.opened }.toSet()
                return !wrapper.isDisposed &&
                    wrapper.languageServer === server &&
                    // the counter's stamps wrap, and a stamp below the mark would then not be older
                    LocalTimeCounter.currentTime() > asked &&
                    opened.containsAll(captured) &&
                    (opened - captured).all(::unchanged) &&
                    buffers.all(Buffer::isCurrent) &&
                    moves.all(FileMove::isCurrent)
            }

            /**
             * Whether [opened], a document opened since the request, has the text that the server read then: a library's
             * read-only source, whose text is the server's own; a document that holds only diagnostics; or one that had no
             * unsaved change then and has none now, whose file has not changed since, which a reload from the disk would
             * have given a later modification stamp (a document's change and a VFS change take theirs from
             * LocalTimeCounter). A save gives the file the stamp of its document's last change, which can be older than
             * the request: a document that had unsaved changes then, [unread], may have been saved since.
             */
            private fun unchanged(opened: OpenedDocument): Boolean {
                val file = opened.file
                if (!file.isWritable && !file.isInLocalFileSystem) return true
                val document = opened.synchronizer?.document ?: return true
                return document !in unread &&
                    !FileDocumentManager.getInstance().isDocumentUnsaved(document) &&
                    file.modificationStamp < asked
            }

            fun flush(): CompletableFuture<Void> =
                CompletableFuture.allOf(
                    *buffers
                        .map { it.synchronizer }
                        .map { sync ->
                            sync.didOpenFuture.thenCompose { sync.flushPendingChanges() }
                        }.toTypedArray(),
                )
        }

        companion object {
            /** Captures the request's sources, under the read action that the request takes. */
            private fun capture(wrapper: LanguageServerWrapper): Snapshot {
                val asked = LocalTimeCounter.currentTime()
                val buffers =
                    wrapper.openedDocuments.map { opened ->
                        val synchronizer =
                            requireNotNull(opened.synchronizer) {
                                "Rename source is no longer open"
                            }
                        Buffer(
                            opened,
                            synchronizer,
                            synchronizer.document.modificationStamp,
                            opened.file.url,
                            opened.file.isValid,
                        )
                    }
                val read = buffers.map { it.synchronizer.document }.toSet()
                val unread =
                    FileDocumentManager
                        .getInstance()
                        .unsavedDocuments
                        .filterNot { it in read }
                        .toSet()
                return Snapshot(wrapper, requireNotNull(wrapper.languageServer), buffers, asked, unread)
            }

            /** Rename is the single-source special case of a preflighted file operation. */
            fun requestFileRename(
                wrapper: LanguageServerWrapper,
                file: VirtualFile,
                name: String,
            ): CompletableFuture<XtcRenameEdit?> {
                require(
                    name.isNotBlank() && name != "." && name != ".." && '/' !in name && '\\' !in name,
                ) {
                    "Enter a single file or directory name"
                }
                return requestFileMoves(
                    wrapper,
                    mapOf(file to Path.of(file.parent.path, name)),
                    "Rename",
                )
            }

            /** Request before disk mutation; VFS before-events are already too late for LSP proof. */
            fun requestFileMoves(
                wrapper: LanguageServerWrapper,
                requested: Map<VirtualFile, Path>,
                command: String = "Move",
            ): CompletableFuture<XtcRenameEdit?> =
                ReadAction.computeBlocking<CompletableFuture<XtcRenameEdit?>, RuntimeException> {
                    if (!FileMoveTargets.valid(requested.mapKeys { Path.of(it.key.path) })) {
                        return@computeBlocking CompletableFuture.completedFuture(null)
                    }
                    val projectRoot = wrapper.project.basePath?.let(Path::of)
                    if (projectRoot != null && requested.keys.any { projectRoot.startsWith(Path.of(it.path)) }) {
                        return@computeBlocking CompletableFuture.completedFuture(null)
                    }
                    val moves =
                        requested.map { (file, target) ->
                            FileMove(file, file.url, file.modificationStamp, target)
                        }
                    val snapshot = capture(wrapper).copy(moves = moves)
                    val operations =
                        moves.map {
                            FileRename(
                                wrapper.toUriString(it.file),
                                it.target
                                    .toFile()
                                    .toURI()
                                    .toString(),
                            )
                        }
                    propose(
                        wrapper,
                        snapshot,
                        xtc = { it.renameFilesProposal(RenameFilesParams(operations)) },
                        fallback = { server ->
                            server.workspaceService.willRenameFiles(RenameFilesParams(operations)).thenApply { edit ->
                                edit?.let {
                                    check(it.changes.isNullOrEmpty()) { "File operations require versioned edits" }
                                    RenameProposal(
                                        WorkspaceEdit().apply {
                                            documentChanges =
                                                buildList {
                                                    addAll(it.documentChanges.orEmpty())
                                                    addAll(
                                                        operations.map { move ->
                                                            Either.forRight(RenameFile(move.oldUri, move.newUri))
                                                        },
                                                    )
                                                }
                                        },
                                    )
                                }
                            }
                        },
                    ) { proposal, graph ->
                        proposal?.let {
                            check(it.edit.changes.isNullOrEmpty()) { "File operations require versioned edits" }
                            XtcRenameEdit(snapshot, it.edit, it.graph?.let(graph::replacement), command, it.textHashes.orEmpty())
                        }
                    }
                }

            /**
             * Capture before flushing changes or sending the request. Comparing only after the response
             * arrives misses edits made while the compiler was calculating the rename. Open/close
             * epochs are represented by the existing OpenedDocument identities.
             */
            @JvmStatic
            fun request(
                wrapper: LanguageServerWrapper,
                file: VirtualFile,
                offset: Int,
                name: String,
            ): CompletableFuture<XtcRenameEdit?> =
                ReadAction.computeBlocking<CompletableFuture<XtcRenameEdit?>, RuntimeException> {
                    val document = requireNotNull(LSPIJUtils.getDocument(file))
                    requireNotNull(wrapper.getOpenedDocument(wrapper.toUri(file))) {
                        "Rename source is no longer open"
                    }
                    val snapshot = capture(wrapper)
                    val params =
                        RenameParams(
                            TextDocumentIdentifier(wrapper.toUriString(file)),
                            LSPIJUtils.toPosition(offset, document),
                            name,
                        )
                    propose(
                        wrapper,
                        snapshot,
                        xtc = { it.renameProposal(params) },
                        fallback = { server ->
                            server.textDocumentService.rename(params).thenApply {
                                it?.let(::RenameProposal)
                            }
                        },
                    ) { proposal, graph ->
                        val edit = proposal?.edit
                        val hasEdits =
                            edit != null &&
                                (
                                    !edit.documentChanges.isNullOrEmpty() ||
                                        !edit.changes.isNullOrEmpty()
                                )
                        if (hasEdits) {
                            XtcRenameEdit(
                                snapshot,
                                edit,
                                proposal.graph?.let(graph::replacement),
                                proved = proposal.textHashes.orEmpty(),
                            )
                        } else {
                            null
                        }
                    }
                }

            /**
             * Flush [snapshot], then ask its server for a proposal: the XTC server through [xtc], which
             * also reports source-graph changes, and any other server through [fallback]. [finish] turns
             * the proposal into an edit against the source graph captured before the request. Cancelling
             * the returned future cancels the server request.
             */
            private fun propose(
                wrapper: LanguageServerWrapper,
                snapshot: Snapshot,
                xtc: (XtcServerProtocol) -> CompletableFuture<RenameProposal?>,
                fallback: (LanguageServer) -> CompletableFuture<RenameProposal?>,
                finish: (RenameProposal?, SourceGraphEdit.Snapshot) -> XtcRenameEdit?,
            ): CompletableFuture<XtcRenameEdit?> {
                val graph = SourceGraphEdit.capture(wrapper.project, wrapper.serverDefinition.id)
                val cancellation = CancellationSupport()
                val result =
                    snapshot
                        .flush()
                        .thenCompose {
                            cancellation.checkCanceled()
                            val server = snapshot.server
                            cancellation.execute(if (server is XtcServerProtocol) xtc(server) else fallback(server))
                        }.thenApply { finish(it, graph) }
                result.whenComplete { _, _ -> if (result.isCancelled) cancellation.cancel() }
                return result
            }
        }
    }
