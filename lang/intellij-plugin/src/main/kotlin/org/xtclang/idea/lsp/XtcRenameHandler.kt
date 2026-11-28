package org.xtclang.idea.lsp

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameHandler
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.LanguageServerItem
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.internal.CompletableFutures
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.RenameOptions
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

// The XTC rename, not LSP4IJ's: it asks the XTC server with xtc/rename, whose proposal carries the change of the
// configured source modules with the edit, and applies both in one command (XtcRenameEdit, SourceGraphEdit).
// textDocument/rename can't carry that change, and the server refuses it when a rename needs it, so this handler
// stays whatever LSP4IJ's own rename checks.

/** Uses Community platform refactoring UI with an atomic stale-document guard. */
class XtcRenameHandler : RenameHandler {
    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return false
        return file.virtualFile?.extension == "x" &&
            CommonDataKeys.EDITOR.getData(dataContext) != null &&
            LanguageServiceAccessor.getInstance(file.project).hasAny(file) { wrapper ->
                wrapper.serverDefinition.id == SERVER_ID &&
                    wrapper.clientFeatures.renameFeature.isEnabled(file) &&
                    supportsRename(wrapper.serverCapabilitiesSync?.renameProvider)
            }
    }

    override fun invoke(
        project: Project,
        editor: Editor,
        file: PsiFile,
        dataContext: DataContext,
    ) {
        val offset = editor.caretModel.offset
        val stamp = editor.document.modificationStamp
        val future =
            LanguageServiceAccessor
                .getInstance(project)
                .getLanguageServers(
                    file,
                    {
                        it.isServerDefinition(SERVER_ID) &&
                            it.isEnabled(file.virtualFile) &&
                            it.renameFeature.isEnabled(file)
                    },
                    { supportsRename(it.serverWrapper.serverCapabilitiesSync?.renameProvider) },
                ).thenCompose { servers ->
                    val server =
                        servers.singleOrNull()
                            ?: return@thenCompose CompletableFuture.completedFuture(null)
                    prepare(server, file, editor, offset)
                }
        CompletableFutures.waitUntilDoneAsync(future, "Preparing rename", file)
        future.whenComplete { prepared, error ->
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed || editor.isDisposed) return@invokeLater
                when {
                    error != null -> {
                        renameFailure(error)?.let { HintManager.getInstance().showErrorHint(editor, it) }
                    }

                    stamp != editor.document.modificationStamp -> {
                        HintManager
                            .getInstance()
                            .showErrorHint(
                                editor,
                                "Source changed; invoke Rename again.",
                            )
                    }

                    prepared == null -> {
                        HintManager
                            .getInstance()
                            .showErrorHint(editor, "This symbol cannot be renamed.")
                    }

                    else -> RenameDialog(file, editor, offset, prepared).show()
                }
            }
        }
    }

    override fun invoke(
        project: Project,
        elements: Array<out PsiElement>,
        dataContext: DataContext,
    ) {
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return
        val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return
        invoke(project, editor, file, dataContext)
    }

    /** Server and original name returned by the prepare-rename step. */
    private data class Prepared(
        val server: LanguageServerItem,
        val name: String,
    )

    private fun prepare(
        server: LanguageServerItem,
        file: PsiFile,
        editor: Editor,
        offset: Int,
    ): CompletableFuture<Prepared?> {
        if (!server.isPrepareRenameSupported) {
            return CompletableFuture.completedFuture(
                ReadAction.computeBlocking<Prepared?, RuntimeException> {
                    LSPIJUtils.getWordRangeAt(editor.document, file, offset)?.let {
                        Prepared(server, editor.document.getText(it))
                    }
                },
            )
        }
        val wrapper = server.serverWrapper
        val opened =
            wrapper.getOpenedDocument(wrapper.toUri(file.virtualFile))
                ?: return CompletableFuture.completedFuture(null)
        val synchronizer = opened.synchronizer ?: return CompletableFuture.completedFuture(null)
        val params =
            ReadAction.computeBlocking<PrepareRenameParams, RuntimeException> {
                PrepareRenameParams(
                    TextDocumentIdentifier(wrapper.toUriString(file.virtualFile)),
                    LSPIJUtils.toPosition(offset, editor.document),
                )
            }
        return synchronizer.didOpenFuture
            .thenCompose { synchronizer.flushPendingChanges() }
            .thenCompose { server.textDocumentService.prepareRename(params) }
            .thenApply { result ->
                if (result == null) return@thenApply null
                ReadAction.computeBlocking<Prepared?, RuntimeException> {
                    val name =
                        when {
                            result.isSecond -> result.second.placeholder

                            result.isFirst -> {
                                LSPIJUtils
                                    .toTextRange(result.first, editor.document)
                                    ?.let(editor.document::getText)
                            }

                            result.third.isDefaultBehavior -> {
                                LSPIJUtils
                                    .getWordRangeAt(
                                        editor.document,
                                        file,
                                        offset,
                                    )?.let(editor.document::getText)
                            }

                            else -> null
                        }
                    name?.let { Prepared(server, it) }
                }
            }
    }

    /** Collects a symbol name while retaining the document stamp needed to reject a stale proposal. */
    private class RenameDialog(
        private val file: PsiFile,
        private val editor: Editor,
        private val offset: Int,
        private val prepared: Prepared,
    ) : XtcRenameDialog(file.project, prepared.name, editor) {
        private val stamp = editor.document.modificationStamp

        init {
            initDialog()
        }

        override fun areButtonsValid(): Boolean = name.enteredName.isNotBlank() && name.enteredName != prepared.name

        override fun doAction() {
            if (editor.document.modificationStamp != stamp) {
                setErrorText("Source changed; cancel and invoke Rename again.")
                return
            }
            val future =
                XtcRenameEdit.request(
                    prepared.server.serverWrapper,
                    file.virtualFile,
                    offset,
                    name.enteredName.trim(),
                )
            close(OK_EXIT_CODE)
            CompletableFutures.waitUntilDoneAsync(future, "Renaming ${prepared.name}", file)
            future.whenComplete { edit, error ->
                ApplicationManager.getApplication().invokeLater {
                    if (file.project.isDisposed || editor.isDisposed) return@invokeLater
                    when {
                        error != null -> {
                            renameFailure(error)?.let { HintManager.getInstance().showErrorHint(editor, it) }
                        }

                        edit == null -> {
                            HintManager
                                .getInstance()
                                .showErrorHint(
                                    editor,
                                    "This rename is not supported or would change another binding.",
                                )
                        }

                        !edit.apply() -> {
                            HintManager
                                .getInstance()
                                .showErrorHint(editor, "Sources changed; invoke Rename again.")
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val SERVER_ID = "xtcLanguageServer"

        fun supportsRename(provider: Either<Boolean, RenameOptions>?): Boolean = provider?.let { it.isRight || it.left == true } == true
    }
}

/** What the rename's hint says when a change of the project overtook the server's proof. */
internal const val RENAME_OVERTAKEN = "The project changed during the rename; invoke Rename again."

/**
 * What the rename's hint says when the server's answer failed with [error]: nothing for a cancellation, as when the user
 * cancels the rename's progress, whose future then has no message; that a change of the project overtook the server's
 * proof, as its ContentModified says, so that the same rename invoked again is proven of the project as it then is;
 * otherwise the server's reason, as that it can't prove the rename while a module does not compile.
 */
internal fun renameFailure(error: Throwable): String? {
    val causes = generateSequence(error) { it.cause }
    return when {
        causes.any { it is CancellationException } -> {
            null
        }

        causes.any { it is ResponseErrorException && it.responseError.code == ResponseErrorCode.ContentModified.value } -> {
            RENAME_OVERTAKEN
        }

        else -> {
            "Rename failed: ${error.cause?.message ?: error.message}"
        }
    }
}
