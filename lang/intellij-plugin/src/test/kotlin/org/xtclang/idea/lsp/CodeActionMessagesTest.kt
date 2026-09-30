package org.xtclang.idea.lsp

import com.redhat.devtools.lsp4ij.JSONUtils
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage
import org.junit.jupiter.api.Test

class CodeActionMessagesTest {
    @Test
    fun `lazy selection carries original action without resolving or applying while listing`() {
        val lazy =
            CodeAction("Generate method").apply {
                data = "detached-handle"
                kind = "quickfix"
            }
        val eager = CodeAction("Import type").apply { edit = WorkspaceEdit(emptyMap()) }
        val command =
            CodeAction("Client action").apply { this.command = Command("Run", "existing") }
        val message =
            ResponseMessage().apply {
                id = "7"
                result =
                    listOf(lazy, eager, command).map { Either.forRight<Command, CodeAction>(it) }
            }
        CodeActionMessages.incoming { assertThat(it).isSameAs(message) }.consume(message)
        assertThat(lazy.edit).isNull()
        assertThat(lazy.command.command).isEqualTo(CodeActionMessages.COMMAND)
        val original = JSONUtils.toModel(lazy.command.arguments.single(), CodeAction::class.java)
        assertThat(original.data.toString()).contains("detached-handle")
        assertThat(original.command).isNull()
        assertThat(original.kind).isEqualTo("quickfix")
        assertThat(eager.command).isNull()
        assertThat(command.command.command).isEqualTo("existing")
        val first = lazy.command
        CodeActionMessages.incoming {}.consume(message)
        assertThat(lazy.command).isSameAs(first)
    }
}
