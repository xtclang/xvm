package org.xtclang.idea

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.redhat.devtools.lsp4ij.DocumentContentSynchronizer
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import java.util.concurrent.TimeUnit.SECONDS

/** LSP4IJ's synchronizer of [file] with the language server, once the server has the file open. */
internal fun CodeInsightTestFixture.synchronizer(file: PsiFile): DocumentContentSynchronizer {
    val server =
        LanguageServiceAccessor
            .getInstance(project)
            .getLanguageServers(file, { true }, { true })
            .get(HANG_SECONDS, SECONDS)
            .single()
            .serverWrapper
    val synchronizer = checkNotNull(server.getOpenedDocument(server.toUri(file.virtualFile))?.synchronizer)
    synchronizer.didOpenFuture.get(HANG_SECONDS, SECONDS)
    return synchronizer
}

/**
 * Runs [typing] in [file] with its edits held, as LSP4IJ holds an edit outside a test until IntelliJ commits the
 * document, which can come after the request that the typing leads to: Ctrl+Space commits the document and then waits
 * on the EDT for the first items, so the held edit went out after the request.
 */
internal fun CodeInsightTestFixture.holdingEdits(
    file: PsiFile,
    typing: CodeInsightTestFixture.() -> Unit,
) {
    // TODO LSP4IJ: UP73 — in a test LSP4IJ sends an edit at once, so the test holds it as LSP4IJ does in the IDE
    val synchronizer = synchronizer(file)
    val document = editor.document
    document.removeDocumentListener(synchronizer)
    try {
        typing()
    } finally {
        // as LSP4IJ adds it
        document.addDocumentListener(synchronizer, synchronizer)
    }
    val held = DocumentContentSynchronizer::class.java.getDeclaredField("changeEvents").apply { isAccessible = true }

    @Suppress("UNCHECKED_CAST")
    val changes = held.get(synchronizer) as MutableList<TextDocumentContentChangeEvent>
    synchronized(changes) { changes += TextDocumentContentChangeEvent(document.text) }
}
