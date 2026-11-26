package org.xvm.lsp.protocol

import com.google.gson.JsonParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class LibrarySettingsTest {
    @TempDir lateinit var directory: Path

    private fun read(json: String) = LibrarySettings.read(JsonParser.parseString(json))

    @Test
    fun `the settings read as their object, with a null module path keeping the build model's, and nothing for none`() {
        assertThat(read("""{"modulePath":["lib","/opt/x.xtc"],"sourceAttachments":[{"module":"Lib","roots":["src"]}]}"""))
            .isEqualTo(LibrarySettings(listOf("lib", "/opt/x.xtc"), listOf(LibraryAttachment("Lib", listOf("src")))))
        assertThat(read("""{"modulePath":null}""")).isEqualTo(LibrarySettings())
        assertThat(read("""{"modulePath":[]}""")?.modulePath).isEmpty()
        assertThat(read("null")).isNull()
        assertThat(LibrarySettings.read(null)).isNull()
    }

    @Test
    fun `anything but the settings' shape is refused`() {
        assertThatThrownBy { read("[]") }.hasMessageContaining("must be an object")
        assertThatThrownBy { read("""{"paths":[]}""") }.hasMessageContaining("Unknown library setting")
        assertThatThrownBy { read("""{"modulePath":"lib"}""") }.hasMessageContaining("must be an array")
        assertThatThrownBy { read("""{"modulePath":[3]}""") }.hasMessageContaining("non-blank strings")
        assertThatThrownBy { read("""{"sourceAttachments":[{"roots":["src"]}]}""") }.hasMessageContaining("module name")
        assertThatThrownBy { read("""{"sourceAttachments":[{"module":"Lib"}]}""") }.hasMessageContaining("ordered roots")
    }

    @Test
    fun `a reader that takes unknown keys gets them, and the rest of the settings`() {
        val unknown = mutableListOf<Set<String>>()
        assertThat(LibrarySettings.read(JsonParser.parseString("""{"modulePath":["lib"],"paths":[]}""")) { unknown += it })
            .isEqualTo(LibrarySettings(listOf("lib")))
        assertThat(unknown).containsExactly(setOf("paths"))
    }

    @Test
    fun `paths name canonical local files, a relative one in the workspace folder`() {
        val base = directory.toUri()
        val files =
            LibrarySettings(
                listOf("second", "./first/../first", directory.resolve("abs").toUri().toString()),
                listOf(LibraryAttachment("Lib", listOf("src"))),
            ).files(base)

        assertThat(files.modulePath).containsExactly(
            directory.resolve("second").toFile().canonicalFile,
            directory.resolve("first").toFile().canonicalFile,
            directory.resolve("abs").toFile().canonicalFile,
        )
        assertThat(files.sourceAttachments).containsOnlyKeys("Lib")
        assertThat(LibrarySettings(listOf(directory.resolve("abs").toUri().toString())).files(null).modulePath).hasSize(1)
    }

    @Test
    fun `a path that names no single local file, or comes twice, and an incomplete or repeated attachment are refused`() {
        val base = directory.toUri()

        assertThatThrownBy { LibrarySettings(listOf("first", "./first")).files(base) }.hasMessageContaining("Duplicate library path")
        assertThatThrownBy { LibrarySettings(listOf("https://example.org/library.xtc")).files(base) }.hasMessageContaining("local file")
        assertThatThrownBy { LibrarySettings(listOf("relative")).files(null) }.hasMessageContaining("exactly one workspace folder")
        assertThatThrownBy { LibrarySettings(listOf(" ")).files(base) }.hasMessageContaining("non-blank")
        assertThatThrownBy { LibrarySettings(sourceAttachments = listOf(LibraryAttachment("", listOf("src")))).files(base) }
            .hasMessageContaining("module name")
        assertThatThrownBy { LibrarySettings(sourceAttachments = listOf(LibraryAttachment("Lib", emptyList()))).files(base) }
            .hasMessageContaining("ordered roots")
        assertThatThrownBy {
            LibrarySettings(
                sourceAttachments = listOf(LibraryAttachment("Lib", listOf("a")), LibraryAttachment("Lib", listOf("b"))),
            ).files(base)
        }.hasMessageContaining("Duplicate source attachment module")
    }
}
