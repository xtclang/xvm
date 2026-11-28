package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

/**
 * Runs rename against real manual-test mixin compositions. Protects covariant and conditional contract
 * families from touching unrelated compositions or accepting colliding defaults.
 */
class XdkManualCompositionRenameTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `manual covariant self family renames without touching the unrelated mixin`() {
        val text = manualCompositionFixture("mixinTests")
        val uri = directory.source("mixinTests", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val at = text.positionOf("Root self();", "self")
            val edit = requireNotNull(adapter.rename(uri, at.line, at.column, "copySelf"))
            assertThat(edit.changes.keys).containsExactly(uri)
            assertThat(edit.changes.getValue(uri)).hasSize(4)
            val changed = apply(text, edit.changes.getValue(uri))
            assertThat(changed).contains("Root copySelf();", "Base copySelf()", "Mix copySelf()", "Mix! self()")
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            val renamed = changed.positionOf("Root copySelf();", "copySelf")
            val reverse = requireNotNull(adapter.rename(uri, renamed.line, renamed.column, "self"))
            assertThat(apply(changed, reverse.changes.getValue(uri))).isEqualTo(text)
        }
    }

    @Test
    fun `manual conditional mixin rename preserves other conditional compositions`() {
        val text = manualCompositionFixture("condMixinTests", "TestConsole")
        val uri = directory.source("condMixinTests", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val constraint = text.positionOf("MixN<Element extends Number>", "Element")
            val declaration = text.positionOf("static mixin MixN<Element", "Element")
            val target = requireNotNull(adapter.findDefinition(uri, constraint.line, constraint.column))
            assertThat(Position(target.startLine, target.startColumn)).isEqualTo(declaration)
            val at = text.positionOf("Int size()", "size")
            val edit = requireNotNull(adapter.rename(uri, at.line, at.column, "width"))
            assertThat(edit.changes.getValue(uri)).hasSize(2)
            val changed = apply(text, edit.changes.getValue(uri))
            assertThat(changed).contains("Int size() = el.size", "Int width() = el.estimateStringLength()")
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @Test
    fun `manual mixin rename refuses a colliding default method`() {
        val text = manualCompositionFixture("mixinTests").replace("interface Root {", "interface Root { Root occupied() = this;")
        val uri = directory.source("mixinTests", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val at = text.positionOf("Root self();", "self")
            assertThat(adapter.rename(uri, at.line, at.column, "occupied")).isNull()
            assertThat(directory.resolve("mixinTests.x").toFile().readText()).isEqualTo(text)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Int size()", "text.size()"])
    fun `conditional source composition renames from declaration and concrete receiver`(anchor: String) {
        val text = conditionalSource()
        val uri = directory.source("Conditional", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val at = text.positionOf(anchor, "size")
            val edit = requireNotNull(adapter.rename(uri, at.line, at.column, "width"))
            val changed = apply(text, edit.changes.getValue(uri))
            assertThat(changed).isEqualTo(text.replace("Int size()", "Int width()").replace("text.size()", "text.width()"))
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            val renamed = changed.positionOf("text.width()", "width")
            val reverse = requireNotNull(adapter.rename(uri, renamed.line, renamed.column, "size"))
            assertThat(apply(changed, reverse.changes.getValue(uri))).isEqualTo(text)
            assertThat(directory.resolve("Conditional.x").toFile().readText()).isEqualTo(text)
        }
    }

    @Test
    fun `conditional composition rename refuses a concrete host collision`() {
        val text = conditionalSource("Int width() = 0;")
        val uri = directory.source("Conditional", text)
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val at = text.positionOf("Int size()", "size")
            assertThat(adapter.rename(uri, at.line, at.column, "width")).isNull()
            assertThat(directory.resolve("Conditional.x").toFile().readText()).isEqualTo(text)
        }
    }

    private fun conditionalSource(body: String = ""): String =
        """
        module Conditional {
            class Box<T>(T value) incorporates conditional Textual<T extends String> { Int size(); $body }
            static mixin Textual<T extends String> into Box<T> { @Override Int size() = value.size; }
            Int read(Box<String> text) = text.size();
            Int unrelated(Box<Int> number) = number.value;
        }
        """.trimIndent()
}
