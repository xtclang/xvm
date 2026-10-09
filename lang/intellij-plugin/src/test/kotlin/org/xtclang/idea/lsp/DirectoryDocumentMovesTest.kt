package org.xtclang.idea.lsp

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.testFramework.LightVirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DirectoryDocumentMovesTest {
    @Test
    fun `pinned LSP4IJ exposes the owned disconnect operation required by the directory move bridge`() {
        val method = DirectoryDocumentMoves.disconnectMethod
        assertThat(method.returnType).isEqualTo(Void.TYPE)
        assertThat(method.trySetAccessible()).isTrue()
    }

    @Test
    fun `overlapping directory moves capture each open descendant once without unrelated files`() {
        val root = File("root", directory = true)
        val old = File("old", root, true)
        val child = File("nested", old, true)
        val source = File("App.x", old)
        val member = File("Member.x", child)
        val similarlyNamed = File("older", root, true)
        val other = File("Other.x", similarlyNamed)
        val moves = listOf(VFileMoveEvent(null, old, root), VFileMoveEvent(null, child, root))

        assertThat(DirectoryDocumentMoves.descendants(moves) { listOf(source, member, source, other) })
            .containsExactly(source, member)
    }

    @Test
    fun `directory rename reconnects descendants but unrelated property changes do not inspect documents`() {
        val root = File("root", directory = true)
        val directory = File("old", root, true)
        val source = File("App.x", directory)
        val rename = VFilePropertyChangeEvent(null, directory, VirtualFile.PROP_NAME, "old", "new")
        assertThat(DirectoryDocumentMoves.descendants(listOf(rename)) { listOf(source) })
            .containsExactly(source)
        val writable = VFilePropertyChangeEvent(null, directory, VirtualFile.PROP_WRITABLE, true, false)
        assertThat(DirectoryDocumentMoves.descendants(listOf(writable)) { error("Unrelated VFS event") }).isEmpty()
    }

    @Test
    fun `individual file moves and renames retain the upstream lifecycle handling`() {
        val root = File("root", directory = true)
        val source = File("App.x", root)
        val events =
            listOf(
                VFileMoveEvent(null, source, root),
                VFilePropertyChangeEvent(null, source, VirtualFile.PROP_NAME, "App.x", "Next.x"),
            )
        assertThat(DirectoryDocumentMoves.descendants(events) { error("Handled by LSP4IJ") }).isEmpty()
    }

    private class File(
        name: String,
        private val folder: VirtualFile? = null,
        private val directory: Boolean = false,
    ) : LightVirtualFile(name) {
        override fun getParent(): VirtualFile? = folder

        override fun isDirectory(): Boolean = directory
    }
}
