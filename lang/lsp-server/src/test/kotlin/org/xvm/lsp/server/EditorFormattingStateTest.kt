package org.xvm.lsp.server

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class EditorFormattingStateTest {
    @Test
    fun `disconnect rejects delayed configuration and requests created after close`() {
        val state = EditorFormattingState()
        val pending = state.request()
        state.close()
        listOf(pending, state.request()).forEach { revision ->
            assertThat(state.accept(revision, mapOf("indentSize" to 2)) { error("closed install") })
                .isFalse()
        }
    }

    @Test
    fun `late replies cannot replace current settings or restore reset configuration`() {
        val state = EditorFormattingState()
        val old = state.request()
        val newest = state.request()
        assertThat(state.accept(newest, mapOf("indentSize" to 2)) {}).isTrue()
        assertThat(state.accept(old, mapOf("indentSize" to 8)) { error("stale install") }).isFalse()
        assertThat(state.config?.indentSize).isEqualTo(2)
        state.accept(state.request(), null) {}
        assertThat(state.config).isNull()
        assertThat(state.accept(newest, mapOf("indentSize" to 2)) { error("reset undone") })
            .isFalse()
    }

    @Test
    fun `malformed settings retain the previous valid snapshot`() {
        val state = EditorFormattingState()
        state.accept(state.request(), mapOf("indentSize" to 2)) {}
        listOf(
            mapOf("indentSize" to 0),
            mapOf("indentSize" to 1.5),
            mapOf("indentSize" to "2"),
            mapOf("insertSpaces" to "false"),
            mapOf("continuationIndentSize" to 1000000),
        ).forEach { invalid ->
            assertThatThrownBy {
                state.accept(state.request(), invalid) { error("invalid install") }
            }.isInstanceOf(IllegalArgumentException::class.java)
            assertThat(state.config?.indentSize).isEqualTo(2)
        }
    }
}
