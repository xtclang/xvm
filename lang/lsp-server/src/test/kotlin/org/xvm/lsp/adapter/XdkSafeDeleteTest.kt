package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkRename
import java.nio.file.Path

/**
 * Checks safe deletion of unused declarations: private ones within their source, public and protected ones within the
 * configured modules. Surviving references, entry points, overriding members, initialization effects and comment loss
 * must prevent removal despite an apparently unused local view.
 */
class XdkSafeDeleteTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(
        strings = [
            "private Int §unused() { return 1; }",
            "private Int §unused(Int input) { return input + kept(); }",
            "private static Int §unused = 3;",
        ],
    )
    fun `unused private methods and constants leave every surviving binding intact`(member: String) {
        query(member, "", expected = true)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Int §unused() { return 1; }",
            "protected Int §unused() { return 1; }",
            "private Int §unused = kept();",
            "private static Int §unused = kept();",
            "private Int §unused() { /* keep this explanation */ return 1; }",
        ],
    )
    fun `module methods, which can be entry points, initialization and comment loss refuse removal`(member: String) {
        query(member, "", expected = false)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "class Box { Int §unused() { return 1; } }",
            "class Box { protected Int §unused(Int input) { return input + kept(); } }",
            "static Int §unused = 3;",
        ],
    )
    fun `unused public and protected members are removed, naming the modules that were checked`(member: String) {
        query(member, "", expected = true, title = "Safely delete member 'unused', unused in Delete")
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            // calls through the interface reach the implementation
            "interface Shape { Int unused(); } class Square implements Shape { @Override Int §unused() { return 4; } }",
            // the subclass's method would override nothing
            "class Base { Int §unused() { return 1; } } class Sub extends Base { @Override Int unused() { return 2; } }",
        ],
    )
    fun `a method that implements, overrides or is overridden is not removed`(member: String) {
        query(member, "", expected = false)
    }

    @Test
    fun `referenced method is not removed`() {
        query("private Int §unused() { return 1; }", "Int caller() { return unused(); }", expected = false)
    }

    @Test
    fun `public member with a closed companion consumer is refused`() {
        val consumer =
            directory.resolve("Delete/Nested.x").toFile().apply {
                parentFile.mkdirs()
                writeText("class Nested { Int read() { return Delete.unused(); } }")
            }
        query("static Int §unused() { return 1; }", "", expected = false)
        assertThat(consumer.readText()).contains("return Delete.unused();")
    }

    @Test
    fun `broken configured neighbor refuses the entire proposal`() {
        directory.resolve("Broken.x").toFile().writeText("module Broken { Missing value; }")
        query("private Int §unused() { return 1; }", "", expected = false)
    }

    private fun query(
        member: String,
        consumer: String,
        expected: Boolean,
        title: String? = null,
    ) {
        CompilerTestSupport.configure()
        val marked =
            """
            module Delete {
                $member
                static Int kept() { return 2; }
                Int run() { return kept(); }
                $consumer
            }
            """.trimIndent()
        val text = marked.replace("§", "")
        val file =
            directory
                .resolve("Delete.x")
                .toFile()
                .canonicalFile
                .apply { writeText(text) }
        val uri = file.toURI().toString()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val at = XdkRename.position(text, marked.indexOf('§'))
            val actions = adapter.getCodeActions(uri, Range(at, at), emptyList()).filter { it.title.startsWith("Safely delete") }
            if (!expected) {
                assertThat(actions).isEmpty()
            } else {
                assertThat(actions).hasSize(1)
                title?.let { assertThat(actions.single().title).isEqualTo(it) }
                val edit = requireNotNull(actions.single().edit)
                assertThat(edit.versioned).isTrue()
                val change = edit.changes.getValue(uri).single()

                fun offset(at: Position) = requireNotNull(XdkRename.offset(text, SemanticModel.Position(at.line, at.column)))
                val changed = text.replaceRange(offset(change.range.start), offset(change.range.end), change.newText)
                assertThat(changed).doesNotContain("unused").contains("return kept();")
                assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            }
            assertThat(file.readText()).isEqualTo(text)
        }
    }
}
