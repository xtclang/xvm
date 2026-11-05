package org.xtclang.idea.lsp

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.GlobalUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.FileRename
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.jsonrpc.Endpoint
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.xtclang.idea.LightEditor
import org.xtclang.idea.awaitUntil
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Undo of a rename that moved a file: the rename that the Undo makes asks the server for no edit, as the Undo restores
 * the files' contents itself; and a refactoring that can no longer apply, as after such an Undo, says so in a
 * notification, not in a modal dialog that would hold the IDE.
 */
class UndoRenameTest {
    @JvmField
    @RegisterExtension
    val editor = LightEditor()

    private val asked = CopyOnWriteArrayList<String>()
    private val server =
        object : Endpoint {
            override fun request(
                method: String,
                parameter: Any?,
            ): CompletableFuture<Any?> {
                asked += method
                return CompletableFuture.completedFuture(null)
            }

            override fun notify(
                method: String,
                parameter: Any?,
            ) {}
        }
    private val rename =
        RenameFilesParams(
            listOf(FileRename(Path.of("/project/After.x").toUri().toString(), Path.of("/project/Before.x").toUri().toString())),
        )

    @Test
    fun `the rename that an Undo makes asks the server for no edit, and one outside an Undo does`() {
        val endpoint = PreflightedRenames().endpoint(server)
        runInEdtAndWait {
            val project = editor.fixture.project
            // a global command, as a rename that moves a file is, whose Undo renames the file back
            val renameBack =
                object : GlobalUndoableAction() {
                    override fun undo() {
                        endpoint.request("workspace/willRenameFiles", rename)
                    }

                    override fun redo() {
                        endpoint.request("workspace/willRenameFiles", rename)
                    }
                }
            CommandProcessor.getInstance().executeCommand(
                project,
                { UndoManager.getInstance(project).undoableActionPerformed(renameBack) },
                "Rename",
                null,
            )
            TestDialogManager.setTestDialog(TestDialog.OK)
            try {
                UndoManager.getInstance(project).undo(null)
                UndoManager.getInstance(project).redo(null)
            } finally {
                TestDialogManager.setTestDialog(TestDialog.DEFAULT)
            }
        }
        assertThat(asked).isEmpty()

        endpoint.request("workspace/willRenameFiles", rename)
        assertThat(asked).containsExactly("workspace/willRenameFiles")
    }

    @Test
    fun `a refactoring that cannot apply says so in a notification, and opens no dialog`() {
        val dialogs = CopyOnWriteArrayList<String>()
        val notified = CopyOnWriteArrayList<Notification>()
        val project = editor.fixture.project
        project.messageBus.connect(editor.fixture.testRootDisposable).subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    notified += notification
                }
            },
        )
        TestDialogManager.setTestDialog { message ->
            dialogs += message
            0
        }
        try {
            runInEdtAndWait {
                XtcFileOperations.submit(project, "Rename After.x", CompletableFuture.completedFuture(null))
                awaitUntil("the refactoring's answer") {
                    (notified.isNotEmpty() || dialogs.isNotEmpty()) || false.also { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
                }
            }
        } finally {
            TestDialogManager.setTestDialog(TestDialog.DEFAULT)
        }
        assertThat(dialogs).isEmpty()
        assertThat(notified.map { it.content }).containsExactly("The compiler cannot safely apply this operation. No changes were made.")
    }
}
