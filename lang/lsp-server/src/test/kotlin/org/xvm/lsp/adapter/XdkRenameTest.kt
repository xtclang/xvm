package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger

class XdkRenameTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `local rename preserves captures and shadowed lambda parameters`() {
        val text =
            "module Rename { Int run() { Int local=1; function Int() f=()->local; " +
                "function Int(Int) g=(Int local)->local; return f()+g(2); } }"
        withSource(text) { adapter ->
            val edit = requireNotNull(adapter.rename(URI, 0, text.indexOf("local"), "renamed"))
            assertThat(edit.versioned).isTrue()
            val changed = apply(text, edit.changes.getValue(URI))
            assertThat(changed).contains("Int renamed=1", "f=()->renamed", "(Int local)->local")
            assertThat(adapter.getCachedResult(URI)?.success).isTrue()
            assertThat(adapter.prepareRename(URI, 0, text.indexOf("local"))?.placeholder).isEqualTo("local")
            assertThat(adapter.compile(URI, changed).success).isTrue()
        }
    }

    @Test
    fun `successful compilation alone does not allow capturing an untouched property`() {
        val text = "module Rename { Int value=10; Int run() { Int local=1; return local+value; } }"
        withSource(text) { adapter ->
            assertThat(adapter.rename(URI, 0, text.indexOf("local"), "value")).isNull()
            assertThat(adapter.getCachedResult(URI)?.diagnostics).isEmpty()
        }
    }

    @Test
    fun `private parameter rename covers reordered named defaults and generic visible indices`() {
        val text = "module Rename { private <T> T pick(T input, Int count=1)=input; String run()=pick(count=2, input=\"yes\"); }"
        withSource(text) { adapter ->
            val edit = requireNotNull(adapter.rename(URI, 0, text.lastIndexOf("input"), "value"))
            assertThat(edit.changes.getValue(URI)).hasSize(3)
            val changed = apply(text, edit.changes.getValue(URI))
            assertThat(changed).contains("T value", "=value", "value=\"yes\"")
            assertThat(adapter.compile(URI, changed).success).isTrue()
        }
    }

    @Test
    fun `overloaded parameter labels only edit the selected private method`() {
        val text =
            "module Rename { private Int pick(Int input)=input; private String pick(String input)=input; " +
                "Int run()=pick(input=1); String text()=pick(input=\"one\"); }"
        withSource(text) { adapter ->
            val edit = requireNotNull(adapter.rename(URI, 0, text.indexOf("input"), "number"))
            val changed = apply(text, edit.changes.getValue(URI))
            assertThat(changed).contains("Int number)=number", "pick(number=1)", "String input)=input", "pick(input=\"one\")")
            assertThat(adapter.compile(URI, changed).success).isTrue()
        }
    }

    @Test
    fun `public parameters constructor properties lambda parameters and members are unavailable`() {
        val text =
            "module Rename { class Item(Int value) {} " +
                "Int run(Int input) { function Int(Int) f=(Int argument)->argument; return f(input); } }"
        withSource(text) { adapter ->
            for (name in listOf("Item", "value", "run", "input", "argument")) {
                assertThat(adapter.prepareRename(URI, 0, text.indexOf(name))).describedAs(name).isNull()
                assertThat(adapter.rename(URI, 0, text.indexOf(name), "other")).describedAs(name).isNull()
            }
        }
    }

    @Test
    fun `method values make private parameter caller closure unavailable`() {
        val text = "module Rename { private Int pick(Int input)=input; Int run() { function Int(Int) f=&pick; return f(1); } }"
        withSource(text) { adapter ->
            assertThat(adapter.prepareRename(URI, 0, text.indexOf("input"))).isNull()
        }
    }

    @Test
    fun `collisions keywords and non-identifiers produce no edits or live diagnostics`() {
        val text = "module Rename { Int run() { Int local=1; Int other=2; return local+other; } }"
        withSource(text) { adapter ->
            for (name in listOf("other", "return", "", "two words", "x.y", "x/*comment*/")) {
                assertThat(adapter.rename(URI, 0, text.indexOf("local"), name)).describedAs(name).isNull()
            }
            assertThat(adapter.getCachedResult(URI)?.diagnostics).isEmpty()
        }
    }

    @Test
    fun `UTF16 positions and CRLF newlines survive a longer name`() {
        val text = "module Rename {\r\n    Int run() { String emoji=\"🙂\"; Int local=1; return local; }\r\n}"
        withSource(text) { adapter ->
            val column = text.lines()[1].indexOf("local")
            val edit = requireNotNull(adapter.rename(URI, 1, column, "longerName"))
            val changed = apply(text, edit.changes.getValue(URI))
            assertThat(changed).contains("Int longerName=1", "return longerName;")
            assertThat(adapter.compile(URI, changed).success).isTrue()
        }
    }

    @Test
    fun `rename in a closed member uses the whole module and rejects changed disk membership`() {
        directory = directory.toRealPath()
        val root = directory.resolve("Rename.x").toFile()
        val member = directory.resolve("Rename/Child.x").toFile()
        val text = "module Rename {}"
        val child = "class Child { private Int pick(Int input)=input; Int run()=pick(input=1); }"
        root.writeText(text)
        member.parentFile.mkdirs()
        member.writeText(child)
        val uri = root.toURI().toString()
        XdkAdapter().use { adapter ->
            val compilation = adapter.compile(uri, text)
            assertThat(compilation.diagnostics).isEmpty()
            val edit = requireNotNull(adapter.rename(member.toURI().toString(), 0, child.indexOf("input"), "value"))
            assertThat(edit.changes.keys).containsExactly(member.toURI().toString())
            assertThat(apply(child, edit.changes.getValue(member.toURI().toString()))).contains("value=1")
            assertThat(member.readText()).isEqualTo(child)
            member.writeText("\n$child")
            assertThat(adapter.rename(member.toURI().toString(), 0, child.indexOf("input"), "value")).isNull()
        }
    }

    @Test
    fun `editing or closing the module cancels a blocked rename proof`() {
        for (close in listOf(false, true)) {
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            val attempts = AtomicInteger()
            val adapter =
                XdkAdapter { source, errors ->
                    if (attempts.incrementAndGet() == 2) {
                        entered.countDown()
                        check(release.await(20, SECONDS))
                    }
                    EmbeddingSupport.instance().compileModule(source, null, errors)
                }
            val text = "module Rename { Int run() { Int local=1; return local; } }"
            try {
                CompilerTestSupport.configure()
                assertThat(adapter.compile(URI, text).success).isTrue()
                val result = adapter.renameAsync(URI, 0, text.indexOf("local"), "value")
                assertThat(entered.await(20, SECONDS)).isTrue()
                if (close) adapter.closeDocument(URI) else adapter.compileAsync(URI, "\n$text")
                assertThat(result.isCancelled).isTrue()
            } finally {
                release.countDown()
                adapter.close()
            }
        }
    }

    private fun withSource(
        text: String,
        test: (XdkAdapter) -> Unit,
    ) {
        XdkAdapter().use { adapter ->
            val result = adapter.compile(URI, text)
            assertThat(result.success).describedAs(result.diagnostics.toString()).isTrue()
            test(adapter)
        }
    }

    private fun apply(
        text: String,
        edits: List<TextEdit>,
    ): String {
        fun offset(position: Position): Int =
            (
                if (position.line == 0) {
                    0
                } else {
                    Regex("\r\n|\r|\n")
                        .findAll(text)
                        .elementAt(position.line - 1)
                        .range.last + 1
                }
            ) + position.column
        return edits
            .sortedWith(compareByDescending<TextEdit> { it.range.start.line }.thenByDescending { it.range.start.column })
            .fold(text) { value, edit -> value.replaceRange(offset(edit.range.start), offset(edit.range.end), edit.newText) }
    }

    private companion object {
        const val URI = "file:///Rename.x"
    }
}
