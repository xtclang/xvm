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
            "module App { $CONTRACTS " +
                "class Forward(First | Second target) delegates Api(target) {} " +
                "class Outer(Forward target) delegates Api(target) {} Int use(Outer value) = value.read(); }"
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
            "module App { interface Api<T> { T read(); } " +
                "class First<T>(T item) implements Api<T> { @Override T read() = item; } " +
                "class Second<T>(T item) implements Api<T> { @Override T read() = item; } " +
                "class Forward(First<Int> | Second<Int> target) delegates Api<Int>(target) {} " +
                "Int use(Forward value) = value.read(); }"
        renamed(text, text.lastIndexOf("read"))
    }

    @Test
    fun `an implicit call to a generated union delegate joins the written family`() {
        val text =
            "module App { $CONTRACTS " +
                "class Forward(First | Second target) delegates Api(target) { Int use() = read(); } }"
        renamed(text, text.lastIndexOf("read"))
    }

    @Test
    fun `one recursive alternative does not hide the other branch`() {
        val text =
            "module App { interface Api { Int read(); } " +
                "class Forward(Left | Right target) delegates Api(target) {} " +
                "class Left(Forward target) delegates Api(target) {} " +
                "class Right implements Api { @Override Int read() = 1; } Int use(Forward value) = value.read(); }"
        renamed(text, text.lastIndexOf("read"))
    }

    @Test
    fun `a collision on one delegate branch refuses all edits`() {
        val text =
            "module App { ${CONTRACTS.replace("Int read() = 1;", "Int read() = 1; Int fetch() = 3;")} " +
                "class Forward(First | Second target) delegates Api(target) {} Int use(Forward value) = value.read(); }"
        workspace(text) { adapter, uri ->
            assertThat(adapter.rename(uri, 0, text.lastIndexOf("read"), "fetch")).isNull()
        }
    }

    private fun renamed(
        text: String,
        column: Int,
    ) {
        workspace(text) { adapter, uri ->
            val edit = requireNotNull(adapter.rename(uri, 0, column, "fetch"))
            val changed = apply(text, edit.changes.getValue(uri))
            assertThat(changed).isEqualTo(text.replace("read", "fetch"))
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            val reverse = requireNotNull(adapter.rename(uri, 0, changed.indexOf("fetch"), "read"))
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

    private fun apply(
        text: String,
        edits: List<TextEdit>,
    ): String =
        edits.sortedByDescending { it.range.start.column }.fold(text) { value, edit ->
            value.replaceRange(edit.range.start.column, edit.range.end.column, edit.newText)
        }

    private companion object {
        const val CONTRACTS =
            "interface Api { Int read(); } " +
                "class First implements Api { @Override Int read() = 1; } " +
                "class Second implements Api { @Override Int read() = 2; }"
    }
}
