package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.lsp.adapter.xdk.XdkLibrarySources
import java.nio.file.Files
import java.nio.file.Path

/** Checks the lines that a stack frame in a bundled library finds in its source's read-only copy. */
class XdkLibrarySourcesTest {
    @Test
    fun `a stack frame of a method with no line of its own has the line of its declaration`() {
        val path = "ecstasy/mgmt/Container.x"
        val lines = Files.readAllLines(Path.of(requireNotNull(XdkLibrarySources.file(ECSTASY, path))))
        val line = requireNotNull(XdkLibrarySources.methodLine(ECSTASY, path, "Control.invoke"))
        assertThat(lines[line - 1]).contains("Tuple invoke(String methodName")
        assertThat(XdkLibrarySources.methodLine(ECSTASY, path, "Control.missing")).isNull()
        assertThat(XdkLibrarySources.methodLine("App", "App.x", "App.run")).isNull()
    }

    private companion object {
        const val ECSTASY = "ecstasy.xtclang.org"
    }
}
