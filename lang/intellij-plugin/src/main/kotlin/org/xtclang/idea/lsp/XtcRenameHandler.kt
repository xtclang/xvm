package org.xtclang.idea.lsp

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.ui.NameSuggestionsField
import com.intellij.refactoring.ui.RefactoringDialog
import com.redhat.devtools.lsp4ij.LSPIJUtils
import com.redhat.devtools.lsp4ij.LanguageServerItem
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import com.redhat.devtools.lsp4ij.internal.CompletableFutures
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.RenameOptions
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

// TODO LSP4IJ: apply rename edits only after checking captured document versions/epochs.
// This native handler can go once upstream provides the guarded application used by XtcRenameEdit.

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
                        HintManager
                            .getInstance()
                            .showErrorHint(
                                editor,
                                "Rename failed: ${error.cause?.message ?: error.message}",
                            )
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

                    else -> {
                        RenameDialog(file, editor, offset, prepared).show()
                    }
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
                            result.isSecond -> {
                                result.second.placeholder
                            }

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

                            else -> {
                                null
                            }
                        }
                    name?.let { Prepared(server, it) }
                }
            }
    }

    private class RenameDialog(
        private val file: PsiFile,
        private val editor: Editor,
        private val offset: Int,
        private val prepared: Prepared,
    ) : RefactoringDialog(file.project, false) {
        private val stamp = editor.document.modificationStamp
        private val name =
            NameSuggestionsField(arrayOf(prepared.name), file.project, FileTypes.PLAIN_TEXT, editor)

        init {
            title = "Rename"
            name.addDataChangedListener { validateButtons() }
            init()
        }

        override fun createCenterPanel(): JComponent = name

        override fun getPreferredFocusedComponent(): JComponent = name.focusableComponent

        override fun hasPreviewButton(): Boolean = false

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
                            HintManager
                                .getInstance()
                                .showErrorHint(
                                    editor,
                                    "Rename failed: ${error.cause?.message ?: error.message}",
                                )
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
