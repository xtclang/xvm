package org.xtclang.idea.playbook.probe

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.DocumentContentSynchronizer
import com.redhat.devtools.lsp4ij.LanguageServiceAccessor
import java.net.URI
import java.nio.file.Path

/** Loaded only in the disposable test IDE; never included in the distributed XTC plugin. */
object DocumentVersions {
    private const val SERVER = "xtcLanguageServer"

    /**
     * The version LSP4IJ last sent the Ecstasy language server for the document at [uri], once that was [text] and no
     * change of it is pending, as its document synchronizer holds it; -1 until then. The LSP console keeps only its
     * last megabyte, which a burst of large messages, such as library sources, can fill before a version is read.
     */
    @JvmStatic
    fun version(
        project: Project,
        uri: String,
        text: String,
    ): Int {
        val path = URI(uri).let { if (it.scheme == "file") Path.of(it).toString() else uri }
        val synchronizer =
            LanguageServiceAccessor
                .getInstance(project)
                .startedServers
                .filter { it.serverDefinition.id == SERVER }
                .flatMap { it.openedDocuments }
                .firstOrNull { it.file.path == path }
                ?.synchronizer ?: return -1
        if (synchronizer.document.text != text || pendingChanges(synchronizer)) return -1
        // package-private in LSP4IJ 0.21.0, public on its main branch
        return DocumentContentSynchronizer::class.java
            .getDeclaredMethod("getVersion")
            .apply { isAccessible = true }
            .invoke(synchronizer) as Int
    }

    private fun pendingChanges(synchronizer: DocumentContentSynchronizer): Boolean {
        val changes =
            DocumentContentSynchronizer::class.java
                .getDeclaredField("changeEvents")
                .apply { isAccessible = true }
                .get(synchronizer) as List<*>
        return synchronized(changes) { changes.isNotEmpty() }
    }
}
