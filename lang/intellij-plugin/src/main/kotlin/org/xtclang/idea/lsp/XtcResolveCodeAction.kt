package org.xtclang.idea.lsp

import com.intellij.openapi.actionSystem.AnActionEvent
import com.redhat.devtools.lsp4ij.commands.LSPCommand
import com.redhat.devtools.lsp4ij.commands.LSPCommandAction
import org.eclipse.lsp4j.CodeAction

/** Only action selection resolves and applies an edit; the normal intention UI owns selection. */
class XtcResolveCodeAction : LSPCommandAction() {
    override fun commandPerformed(
        command: LSPCommand,
        event: AnActionEvent,
    ) {
        val project = event.project ?: return
        val wrapper = getLanguageServer(event)?.serverWrapper ?: return
        if (wrapper.serverDefinition.id != CompilerSettings.SERVER_ID) return
        val action = command.getArgumentAt(0, CodeAction::class.java) ?: return
        XtcFileOperations.submit(
            project,
            action.title,
            XtcRenameEdit.requestAction(wrapper, action),
        )
    }
}
