package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkDependency
import org.xvm.lsp.adapter.xdk.toDependency
import java.nio.file.Path
import kotlin.io.path.readText

class XdkColorValueTest {
    @Test
    fun `fixture exposes initializers and reordered named channels only when enabled`() {
        val source = Path.of(System.getProperty("xtc.composite.root"), "lang/test-fixtures/color-values/ColorPrototype.x").readText()
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            assertThat(adapter.capabilities).doesNotContain(AdapterCapability.DOCUMENT_COLOR)
            assertThat(adapter.getDocumentColors(URI)).isEmpty()
        }
        XdkAdapter(colorPrototype = true).use { adapter ->
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            assertThat(adapter.capabilities).contains(AdapterCapability.DOCUMENT_COLOR)
            assertThat(adapter.getDocumentColors(URI).map { it.color })
                .containsExactlyInAnyOrder(rgba(255, 128, 0), rgba(200, 80, 40, 128))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["\n", "\r\n", "\r"])
    fun `picker edits preserve named order comments aliases and UTF-16 source text`(newline: String) {
        val source =
            """
            module ColorPrototype {
                const Rgba(UInt8 red, UInt8 green, UInt8 blue, UInt8 alpha = 255) {}
                typedef Rgba as Tint;
                Tint sample() {
                    String marker = "😀"; return new Tint(blue = /* keep blue */ 0x40,
                        red = 100, green = 2_0);
                }
            }
            """.trimIndent().replace("\n", newline)
        XdkAdapter(colorPrototype = true).use { adapter ->
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            val color = adapter.getDocumentColors(URI).single()
            assertThat(color.color).isEqualTo(rgba(100, 20, 64))
            val wanted = rgba(1, 2, 3, 64)
            val edit = adapter.getColorPresentations(URI, color.range, wanted).single().textEdit
            val expected = source.replace("0x40", "3").replace("red = 100", "red = 1").replace("green = 2_0)", "green = 2, alpha = 64)")
            val changed = apply(source, edit)
            assertThat(changed).isEqualTo(expected)
            assertThat(adapter.compile(URI, changed).diagnostics).isEmpty()
            val after = adapter.getDocumentColors(URI).single()
            assertThat(after.color).isEqualTo(wanted)
            assertThat(apply(changed, adapter.getColorPresentations(URI, after.range, wanted).single().textEdit)).isEqualTo(changed)
        }
    }

    @Test
    fun `resolved binary import alias works and an unrelated same-named type does not`() {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val library = EmbeddingSupport.instance().compileModule(Source(library(), URI), null, errors)
        assertThat(library.succeeded()).describedAs(errors.errors.toString()).isTrue()
        val binary = XdkDependency.fromBinary(library.toDependency().bytes())
        val source =
            """
            module Consumer {
                package colors import ColorPrototype;
                import colors.Rgba as Tint;
                const Rgba(UInt8 red, UInt8 green, UInt8 blue, UInt8 alpha = 255) {}
                Tint chosen() = new Tint(10, 20, 30);
                Rgba unrelated() = new Rgba(10, 20, 30);
            }
            """.trimIndent()
        XdkAdapter(colorPrototype = true).use { adapter ->
            adapter.replaceDependencies(listOf(binary))
            assertThat(adapter.compile(CONSUMER, source).diagnostics).isEmpty()
            val color = adapter.getDocumentColors(CONSUMER).single()
            assertThat(color.color).isEqualTo(rgba(10, 20, 30))
            val changed = apply(source, adapter.getColorPresentations(CONSUMER, color.range, rgba(40, 50, 60)).single().textEdit)
            assertThat(changed).isEqualTo(source.replace("new Tint(10, 20, 30)", "new Tint(40, 50, 60)"))
            assertThat(adapter.compile(CONSUMER, changed).diagnostics).isEmpty()
            assertThat(adapter.getDocumentColors(CONSUMER).single().color).isEqualTo(rgba(40, 50, 60))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["red, 2, 3", "one, 2, 3", "1 + 2, 2, 3"])
    fun `dynamic values references and compound constant expressions are not rewritten`(arguments: String) {
        val source =
            library(
                """
                Rgba value(UInt8 red) {
                    UInt8 one = 1;
                    return new Rgba($arguments);
                }
                """.trimIndent(),
            )
        XdkAdapter(colorPrototype = true).use { adapter ->
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            assertThat(adapter.getDocumentColors(URI)).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["new Rgba(256, 2, 3)", "new Rgba(-1, 2, 3)", "new Rgba(1, 2,", "new Rgba(1, 2, missing)"])
    fun `invalid edits and close clear all prior colors and presentations`(expression: String) {
        val valid = library("Rgba value() = new Rgba(1, 2, 3);")
        XdkAdapter(colorPrototype = true).use { adapter ->
            assertThat(adapter.compile(URI, valid).diagnostics).isEmpty()
            val before = adapter.getDocumentColors(URI).single()
            assertThat(adapter.getColorPresentations(URI, Range(Position(0, 0), Position(0, 1)), before.color)).isEmpty()
            assertThat(adapter.compile(URI, library("Rgba value() = $expression;")).success).isFalse()
            assertThat(adapter.getDocumentColors(URI)).isEmpty()
            assertThat(adapter.getColorPresentations(URI, before.range, before.color)).isEmpty()
            assertThat(adapter.compile(URI, valid).diagnostics).isEmpty()
            adapter.closeDocument(URI)
            assertThat(adapter.getDocumentColors(URI)).isEmpty()
            assertThat(adapter.getColorPresentations(URI, before.range, before.color)).isEmpty()
        }
    }

    @Test
    fun `changed fixture defaults are not treated as the known color contract`() {
        XdkAdapter(colorPrototype = true).use { adapter ->
            val source = library("Rgba value() = new Rgba(1, 2, 3);").replace("alpha = 255", "alpha = 0")
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            assertThat(adapter.getDocumentColors(URI)).isEmpty()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["1, 2, 3", "1, 2, 3,", "red = 1, blue = 3, green = 2, /* trailing */"])
    fun `continuous picker values quantize to bytes and alpha insertion preserves trailing separators`(arguments: String) {
        val source = library("Rgba value() = new Rgba($arguments);")
        XdkAdapter(colorPrototype = true).use { adapter ->
            assertThat(adapter.compile(URI, source).diagnostics).isEmpty()
            val before = adapter.getDocumentColors(URI).single()
            val chosen = ColorValue(0.5, 0.25, 0.75, 0.5)
            val changed = apply(source, adapter.getColorPresentations(URI, before.range, chosen).single().textEdit)
            assertThat(adapter.compile(URI, changed).diagnostics).isEmpty()
            assertThat(adapter.getDocumentColors(URI).single().color).isEqualTo(rgba(128, 64, 191, 128))
            if ("/* trailing */" in source) assertThat(changed).contains("/* trailing */")
        }
    }

    private fun library(body: String = "") =
        """
        module ColorPrototype {
            const Rgba(UInt8 red, UInt8 green, UInt8 blue, UInt8 alpha = 255) {}
            $body
        }
        """.trimIndent()

    private fun rgba(
        red: Int,
        green: Int,
        blue: Int,
        alpha: Int = 255,
    ) = ColorValue(red / 255.0, green / 255.0, blue / 255.0, alpha / 255.0)

    private fun apply(
        source: String,
        edit: TextEdit,
    ): String {
        fun offset(at: Position): Int {
            val starts = listOf(0) + Regex("\r\n|\r|\n").findAll(source).map { it.range.last + 1 }.toList()
            return starts[at.line] + at.column
        }
        return source.replaceRange(offset(edit.range.start), offset(edit.range.end), edit.newText)
    }

    private companion object {
        const val URI = "file:///ColorPrototype.x"
        const val CONSUMER = "file:///Consumer.x"
    }
}
