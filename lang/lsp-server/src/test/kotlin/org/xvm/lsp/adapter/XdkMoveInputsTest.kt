package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkMoveInputs
import java.nio.file.Files
import java.nio.file.Path

class XdkMoveInputsTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `move snapshots detect changed bytes and newly introduced resource members`() {
        val file = Files.writeString(directory.resolve("data.txt"), "before")
        val captured = requireNotNull(XdkMoveInputs.capture(setOf(directory.toFile())) { false })
        assertThat(captured.isCurrent { false }).isTrue()
        Files.writeString(file, "after")
        assertThat(captured.isCurrent { false }).isFalse()
        val next = requireNotNull(XdkMoveInputs.capture(setOf(directory.toFile())) { false })
        Files.writeString(directory.resolve("extra.txt"), "added")
        assertThat(next.isCurrent { false }).isFalse()
    }

    @Test
    fun `incoming symbolic links cannot acquire captured ownership`() {
        Files.createSymbolicLink(directory.resolve("link"), directory.resolve("missing"))
        assertThat(XdkMoveInputs.capture(setOf(directory.toFile())) { false }).isNull()
    }
}
