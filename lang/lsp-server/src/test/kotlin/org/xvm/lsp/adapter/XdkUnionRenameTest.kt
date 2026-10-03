package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import java.nio.file.Path

class XdkUnionRenameTest {
    @TempDir lateinit var directory: Path

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun `either declaration or the union call renames every possible contract`(entry: Int) {
        val body = "class First { Int read() = 1; } class Second { Int read() = 2; } Int use(First | Second target) = target.read();"
        val unrelated = "class Other { Int read() = 3; }"
        workspace("module App { $body $unrelated }") { adapter, uri, text ->
            val column =
                Regex("read")
                    .findAll(text)
                    .elementAt(entry)
                    .range.first
            val changed = apply(text, requireNotNull(adapter.rename(uri, 0, column, "fetch")).changes.getValue(uri))
            assertThat(changed).isEqualTo("module App { ${body.replace("read", "fetch")} $unrelated }")
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            val undo = requireNotNull(adapter.rename(uri, 0, changed.indexOf("fetch"), "read"))
            assertThat(apply(changed, undo.changes.getValue(uri))).isEqualTo(text)
        }
    }

    @Test
    fun `nested union legs and direct consumers retain the same targets`() {
        val body =
            "class First { Int read() = 1; } class Second { Int read() = 2; } class Third { Int read() = 3; } " +
                "Int use(First | Second | Third target) = target.read(); Int direct(Second target) = target.read();"
        workspace("module App { $body }") { adapter, uri, text ->
            val edit = requireNotNull(adapter.rename(uri, 0, text.lastIndexOf("read"), "fetch"))
            val changed = apply(text, edit.changes.getValue(uri))
            assertThat(changed).isEqualTo(text.replace("read", "fetch"))
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @Test
    fun `a closed cross-module union consumer joins otherwise independent source families`() {
        val first = "module First { class Box { Int read() = 1; } }"
        val second = "module Second { class Box { Int read() = 2; } }"
        val consumer =
            "module Consumer { package one import First; package two import Second; " +
                "Int use(one.Box | two.Box target) = target.read(); }"
        val sources = mapOf("First" to first, "Second" to second, "Consumer" to consumer)
        val uris =
            sources.mapValues { (name, text) ->
                directory
                    .resolve("$name.x")
                    .toFile()
                    .apply { writeText(text) }
                    .canonicalFile
                    .toURI()
                    .toString()
            }
        XdkAdapter().use { adapter ->
            adapter.replaceSourceModules(
                sources.keys.map { name ->
                    XdkSourceModule(name, uris.getValue(name), if (name == "Consumer") setOf("First", "Second") else emptySet())
                },
            )
            assertThat(adapter.compile(uris.getValue("First"), first).diagnostics).isEmpty()
            val edit = requireNotNull(adapter.rename(uris.getValue("First"), 0, first.indexOf("read"), "fetch"))
            assertThat(edit.changes.keys).containsExactlyInAnyOrderElementsOf(uris.values)
            sources.forEach { (name, text) ->
                assertThat(apply(text, edit.changes.getValue(uris.getValue(name)))).isEqualTo(text.replace("read", "fetch"))
            }
        }
    }

    @Test
    fun `renaming a receiver class may reorder union legs without rebinding calls`() {
        val text =
            "module App { class First { Int read() = 1; } class Second { Int read() = 2; } " +
                "Int use(First | Second target) = target.read(); }"
        workspace(text) { adapter, uri, _ ->
            val edit = requireNotNull(adapter.rename(uri, 0, text.indexOf("First"), "Zed"))
            val changed = apply(text, edit.changes.getValue(uri))
            assertThat(changed).isEqualTo(text.replace("First", "Zed"))
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @Test
    fun `collisions on one leg refuse the entire edit`() {
        val text =
            "module App { class First { Int read() = 1; Int fetch() = 3; } class Second { Int read() = 2; } " +
                "Int use(First | Second target) = target.read(); }"
        workspace(text) { adapter, uri, _ ->
            assertThat(adapter.rename(uri, 0, text.lastIndexOf("read"), "fetch")).isNull()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["Int", "String", "List<String>", "Map<String, Int>", "List<Int | String>"])
    fun `generic union receivers preserve nested substitutions during rename`(argument: String) {
        val text =
            "module App { class First<T>(T value) { T read() = value; } class Second<T>(T value) { T read() = value; } " +
                "$argument use(First<$argument> | Second<$argument> target) = target.read(); }"
        workspace(text) { adapter, uri, _ ->
            val changed = apply(text, requireNotNull(adapter.rename(uri, 0, text.lastIndexOf("read"), "fetch")).changes.getValue(uri))
            assertThat(changed).isEqualTo(text.replace("read", "fetch"))
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
            val reverse = requireNotNull(adapter.rename(uri, 0, changed.indexOf("fetch"), "read"))
            assertThat(apply(changed, reverse.changes.getValue(uri))).isEqualTo(text)
        }
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "mixin Mark(String label) into Object {} Int use((@Mark(\"one\") First) | (@Mark(\"two\") Second) target) = target.read();",
            "mixin Loud into First { @Override Int read() = super() + 1; } Int use((@Loud First) | Second target) = target.read();",
        ],
    )
    fun `annotated union receivers preserve annotation values and composed methods`(body: String) {
        val text = "module App { class First { Int read() = 1; } class Second { Int read() = 2; } $body }"
        workspace(text) { adapter, uri, _ ->
            val changed = apply(text, requireNotNull(adapter.rename(uri, 0, text.lastIndexOf("read"), "fetch")).changes.getValue(uri))
            assertThat(changed).isEqualTo(text.replace("read", "fetch"))
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @Test
    fun `renaming a substituted source type translates identities inside receiver type arguments`() {
        val text =
            "module App { class Payload {} class First<T>(T value) { T read() = value; } " +
                "class Second<T>(T value) { T read() = value; } Payload use(First<Payload> | Second<Payload> target) = target.read(); }"
        workspace(text) { adapter, uri, _ ->
            val changed = apply(text, requireNotNull(adapter.rename(uri, 0, text.indexOf("Payload"), "Content")).changes.getValue(uri))
            assertThat(changed).isEqualTo(text.replace("Payload", "Content"))
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    @Test
    fun `source formal arguments retain their declaration identity`() {
        val text = "module App { class First<T>(T value) { T read() = value; } class Second<T>(T value) { T read() = value; } " +
            "class Consumer<T>(First<T> | Second<T> target) { T use() = target.read(); } }"
        workspace(text) { adapter, uri, _ ->
            val changed = apply(text, requireNotNull(adapter.rename(uri, 0, text.indexOf("read"), "fetch")).changes.getValue(uri))
            assertThat(changed).isEqualTo(text.replace("read", "fetch"))
            assertThat(adapter.compile(uri, changed).diagnostics).isEmpty()
        }
    }

    private fun workspace(
        text: String,
        check: (XdkAdapter, String, String) -> Unit,
    ) {
        val source = directory.resolve("App.x").toFile().apply { writeText(text) }
        val uri = source.toURI().toString()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).describedAs(text).isEmpty()
            check(adapter, uri, text)
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
}
