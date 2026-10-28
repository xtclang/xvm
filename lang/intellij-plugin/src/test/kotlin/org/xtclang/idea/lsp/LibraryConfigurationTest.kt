package org.xtclang.idea.lsp

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class LibraryConfigurationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `library round trip preserves unrelated settings and ordered roots`() {
        Files.createDirectories(directory.resolve("primary"))
        Files.createDirectories(directory.resolve("fallback"))
        val options = LibraryOptions(listOf("fallback", "primary"), listOf(LibraryAttachment("Library", listOf("primary", "fallback"))))
        val content = """{"xtc":{"compiler":{"sourceModules":[],"other":42},"languageService":{"inlayHints":false}}}"""
        val replacement = LibraryConfiguration.configure(content, options, directory.toUri())
        assertThat(LibraryConfiguration.read(replacement)).isEqualTo(options)
        assertThat(replacement).contains("42", "inlayHints", "sourceModules")
        assertThat(
            LibraryConfiguration.read(LibraryConfiguration.configure(replacement, LibraryOptions(), directory.toUri())).modulePath,
        ).isNull()
        assertThat(
            LibraryConfiguration
                .read(
                    LibraryConfiguration.configure(replacement, LibraryOptions(emptyList()), directory.toUri()),
                ).modulePath,
        ).isEmpty()
    }

    @Test
    fun `invalid missing aliased and remote paths cannot be persisted`() {
        Files.createDirectories(directory.resolve("sources"))
        listOf(listOf("missing"), listOf("https://example.org/library.xtc"), listOf("sources", "./sources")).forEach { paths ->
            assertThatThrownBy {
                LibraryConfiguration.configure(null, LibraryOptions(paths), directory.toUri())
            }.isInstanceOf(IllegalArgumentException::class.java)
        }
        assertThatThrownBy {
            LibraryConfiguration.configure(
                null,
                LibraryOptions(sourceAttachments = listOf(LibraryAttachment("", listOf("sources")))),
                directory.toUri(),
            )
        }.hasMessageContaining("module name")
    }
}
