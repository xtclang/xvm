package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

/** Concrete conditional members need not have a matching formal host declaration. */
class XdkConditionalRenameTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(strings = ["measure", "length"])
    fun `conditional members without host contracts rename within one module`(member: String) {
        val text = library("Int read(Box<String> text) = text.measure() + text.length;")
        val uri = directory.source("Library", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val at = text.positionOf("text.$member", member)
            val edit = requireNotNull(adapter.rename(uri, at.line, at.column, "width"))
            val changed = apply(text, edit, uri)
            assertThat(changed).isEqualTo(text.replace(member, "width"))
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["measure", "length"])
    fun `conditional members instantiated only by a closed consumer join source rename`(member: String) {
        val library = library()
        val consumer =
            "module Consumer { package lib import Library; Int read(lib.Box<String> text) = text.measure() + text.length; }"
        directory.libraryAndConsumer(library, consumer, initialized = true) { adapter, libraryUri, consumerUri ->
            assertThat(adapter.compile(libraryUri, library).diagnostics).isEmpty()
            val at = library.positionOf("Int $member", member)
            val edit = requireNotNull(adapter.rename(libraryUri, at.line, at.column, "width"))
            assertThat(edit.changes.keys).containsExactlyInAnyOrder(libraryUri, consumerUri)
            assertThat(apply(library, edit, libraryUri))
                .isEqualTo(library.replace(member, "width"))
            assertThat(apply(consumer, edit, consumerUri))
                .isEqualTo(consumer.replace(member, "width"))
        }
    }

    @Test
    fun `mutually exclusive conditional methods remain separate rename families`() {
        val text =
            """
            module Library {
                class Box<T>(T value)
                        incorporates conditional Textual<T extends String>
                        incorporates conditional Numeric<T extends Number> {}
                static mixin Textual<T extends String> into Box<T> { Int measure() = value.size; }
                static mixin Numeric<T extends Number> into Box<T> { Int measure() = 1; }
                Int read(Box<String> text, Box<Int> number) = text.measure() + number.measure();
            }
            """.trimIndent()
        val uri = directory.source("Library", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val at = text.positionOf("text.measure", "measure")
            val edit = requireNotNull(adapter.rename(uri, at.line, at.column, "width"))
            val changed = apply(text, edit, uri)
            assertThat(changed).isEqualTo(
                text
                    .replace("Int measure() = value.size", "Int width() = value.size")
                    .replace("text.measure()", "text.width()"),
            )
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @Test
    fun `conditional contract collision refuses even when the proposed graph compiles`() {
        val library =
            """
            module Library {
                interface First { Int read(); }
                interface Second { Int fetch(); }
                class Box<T> incorporates conditional Both<T extends String> {}
                static mixin Both<T extends String> into Box<T> implements First, Second {}
            }
            """.trimIndent()
        val consumer = "module Consumer { package lib import Library; lib.Box<String>? saved = Null; }"
        directory.libraryAndConsumer(library, consumer) { adapter, libraryUri, consumerUri ->
            assertThat(adapter.compile(consumerUri, consumer).diagnostics).isEmpty()
            val at = library.positionOf("Int read", "read")
            assertThat(adapter.rename(libraryUri, at.line, at.column, "fetch")).isNull()
            assertThat(adapter.compile(libraryUri, library.replace("read", "fetch")).diagnostics).isEmpty()
            assertThat(adapter.compile(consumerUri, consumer).diagnostics).isEmpty()
        }
    }

    private fun library(body: String = ""): String =
        """
        module Library {
            class Box<T>(T value) incorporates conditional Textual<T extends String> {}
            static mixin Textual<T extends String> into Box<T> {
                Int measure() = value.size;
                Int length.get() = value.size;
            }
            $body
        }
        """.trimIndent()
}
