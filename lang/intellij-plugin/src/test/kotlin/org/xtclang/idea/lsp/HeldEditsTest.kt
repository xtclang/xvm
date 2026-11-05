package org.xtclang.idea.lsp

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.xtclang.idea.LightEditor
import org.xtclang.idea.awaitCall
import org.xtclang.idea.holdingEdits
import org.xtclang.idea.synchronizer
import java.util.concurrent.CompletableFuture

/**
 * The edits that LSP4IJ holds are sent, and waited for, where a wait holds nothing up: on a pooled thread in a read
 * action, as completion's contributors and parameter info's handler run, and never on the EDT or with write access.
 */
class HeldEditsTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor()

    @Test
    fun `on the EDT or with write access the wait for sent edits returns at once`() {
        // an edit that is not sent while the waits run; the bound only ends a hang
        val unsent = CompletableFuture<Void>()
        try {
            awaitCall {
                runInEdtAndWait {
                    assertThat(ApplicationManager.getApplication().isDispatchThread).isTrue()
                    awaitSent(unsent)
                    WriteAction.run<RuntimeException> { awaitSent(unsent) }
                }
            }
            assertThat(unsent.isDone).isFalse()
        } finally {
            // a wait that hung then ends, and the EDT is free for the fixture's tear-down
            unsent.complete(null)
        }
    }

    @Test
    fun `in a read action nothing held sends nothing, and a held edit is sent before the call returns`() {
        val (file, synchronizer) = editor.open("Held.x", "module M {\n    <caret>\n}\n") { file -> file to synchronizer(file) }
        val project = editor.fixture.project
        // as on a caret move: with nothing held, the last edit's future stays the one it was
        val last = synchronizer.flushPendingChanges()
        ReadAction.runBlocking<RuntimeException> { sendHeldEdits(project, file.virtualFile) }
        assertThat(synchronizer.flushPendingChanges()).isSameAs(last)
        runInEdtAndWait { editor.fixture.holdingEdits(file) { type("Int x = 1;") } }
        ReadAction.runBlocking<RuntimeException> { sendHeldEdits(project, file.virtualFile) }
        assertThat(synchronizer.flushPendingChanges()).isNotSameAs(last).isDone()
    }
}
