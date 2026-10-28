package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

class XdkUnionDelegationRenameTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 3])
    fun `a union inside two delegate layers retains every contract and receiver`(entry: Int) {
        val text =
            """
            module App {
                interface Api { Int read(); }
                class First implements Api { @Override Int read() = 1; }
                class Second implements Api { @Override Int read() = 2; }
                class Forward(First | Second target) delegates Api(target) {}
                class Outer(Forward target) delegates Api(target) {}
                Int use(Outer value) = value.read();
            }
            """.trimIndent()
        renamed(
            text,
            Regex("read")
                .findAll(text)
                .elementAt(entry)
                .range.first,
        )
    }

    @Test
    fun `generic union delegate branches preserve concrete substitutions`() {
        val text =
            """
            module App {
                interface Api<T> { T read(); }
                class First<T>(T item) implements Api<T> { @Override T read() = item; }
                class Second<T>(T item) implements Api<T> { @Override T read() = item; }
                class Forward(First<Int> | Second<Int> target) delegates Api<Int>(target) {}
                Int use(Forward value) = value.read();
            }
            """.trimIndent()
        renamed(text, text.lastIndexOf("read"))
    }

    @Test
    fun `an implicit call to a generated union delegate joins the written family`() {
        val text =
            """
            module App {
                interface Api { Int read(); }
                class First implements Api { @Override Int read() = 1; }
                class Second implements Api { @Override Int read() = 2; }
                class Forward(First | Second target) delegates Api(target) {
                    Int use() = read();
                }
            }
            """.trimIndent()
        renamed(text, text.lastIndexOf("read"))
    }

    @Test
    fun `one recursive alternative does not hide the other branch`() {
        val text =
            """
            module App {
                interface Api { Int read(); }
                class Forward(Left | Right target) delegates Api(target) {}
                class Left(Forward target) delegates Api(target) {}
                class Right implements Api { @Override Int read() = 1; }
                Int use(Forward value) = value.read();
            }
            """.trimIndent()
        renamed(text, text.lastIndexOf("read"))
    }

    @Test
    fun `a collision on one delegate branch refuses all edits`() {
        val text =
            """
            module App {
                interface Api { Int read(); }
                class First implements Api {
                    @Override Int read() = 1;
                    Int fetch() = 3;
                }
                class Second implements Api { @Override Int read() = 2; }
                class Forward(First | Second target) delegates Api(target) {}
                Int use(Forward value) = value.read();
            }
            """.trimIndent()
        workspace(text) { adapter, uri ->
            assertThat(adapter.renameAt(uri, text, text.lastIndexOf("read"), "fetch")).isNull()
        }
    }

    private fun renamed(
        text: String,
        offset: Int,
    ) {
        workspace(text) { adapter, uri ->
            val edit = requireNotNull(adapter.renameAt(uri, text, offset, "fetch"))
            val changed = apply(text, edit.changes.getValue(uri))
            assertThat(changed).isEqualTo(text.replace("read", "fetch"))
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            val reverse = requireNotNull(adapter.renameAt(uri, changed, changed.indexOf("fetch"), "read"))
            assertThat(apply(changed, reverse.changes.getValue(uri))).isEqualTo(text)
        }
    }

    private fun workspace(
        text: String,
        check: (XdkAdapter, String) -> Unit,
    ) {
        val source = directory.resolve("App.x").toFile().apply { writeText(text) }
        val uri = source.toURI().toString()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).describedAs(text).isEmpty()
            check(adapter, uri)
            assertThat(source.readText()).isEqualTo(text)
        }
    }

    private fun XdkAdapter.renameAt(
        uri: String,
        text: String,
        offset: Int,
        name: String,
    ): WorkspaceEdit? {
        val prefix = text.take(offset)
        return rename(uri, prefix.count { it == '\n' }, prefix.substringAfterLast('\n').length, name)
    }

    private fun apply(
        text: String,
        edits: List<TextEdit>,
    ): String {
        fun offset(position: Position) = text.lineSequence().take(position.line).sumOf { it.length + 1 } + position.column
        return edits.sortedByDescending { offset(it.range.start) }.fold(text) { value, edit ->
            value.replaceRange(offset(edit.range.start), offset(edit.range.end), edit.newText)
        }
    }
}
