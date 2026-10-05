package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.CompiledSemantics
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.XdkDependency
import org.xvm.lsp.adapter.xdk.XdkSourceModule
import org.xvm.lsp.adapter.xdk.compiledSemantics
import java.nio.file.Files
import java.nio.file.Path

class XdkMonikerTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `unchanged recompilation and checkout relocation preserve exports while versions and overloads differ`() {
        val first = compile(LIBRARY, "file:///first/Library.x")
        val second = compile(LIBRARY, "file:///second/Library.x")
        val changed = compile(LIBRARY.replace("= n;", "= n + 1;"), "file:///other/Library.x")
        val exports =
            first.models.single().symbols.filter { it.name == "pick" }.map { symbol ->
                val at = requireNotNull(symbol.declaration).start
                first.models
                    .single()
                    .monikersAt(at.line, at.column)
                    .single()
            }
        assertThat(exports).hasSize(2).doesNotHaveDuplicates().allMatch { it.kind == SymbolMoniker.Kind.EXPORT }
        assertThat(moniker(first, LIBRARY, "pick(Int"))
            .isEqualTo(moniker(second, LIBRARY, "pick(Int"))
            .isNotEqualTo(moniker(changed, LIBRARY, "pick(Int"))
        assertThat(first.artifact!!.bytes()).isNotEqualTo(second.artifact!!.bytes())
        assertThat(monikers(first.models.single(), LIBRARY, "T>")).isEmpty()
    }

    @Test
    fun `source indexed and binary only imports match exact exported overloads`() {
        val library = compile(LIBRARY)
        val source = library.artifact!!
        val binary = XdkDependency.fromBinary(source.bytes())
        assertThat(source.revision).isNotEqualTo(binary.revision)
        listOf(source, binary).forEach { dependency ->
            val consumer = compile(CONSUMER, dependencies = listOf(dependency))
            listOf(
                "pick(Int" to "pick(1)",
                "pick(String" to "pick(\"x\")",
                "Box<T>" to "Box<Int>",
                "echo(T" to "echo(1)",
                "echo(T" to "echo(\"x\")",
            ).forEach { (declaration, use) ->
                val exported = moniker(library, LIBRARY, declaration)
                assertThat(moniker(consumer, CONSUMER, use))
                    .isEqualTo(exported.copy(kind = SymbolMoniker.Kind.IMPORT))
            }
        }
    }

    @Test
    fun `private declarations stay local and registers unresolved names and failed attempts have no artifact identity`() {
        val text =
            """
            module App {
                private Int hidden = 1;
                private class Hidden { Int value = 2; }
                Int read(Int argument) {
                    Int local = argument;
                    return local + hidden;
                }
            }
            """.trimIndent()
        val result = compile(text)
        assertThat(moniker(result, text, "hidden =").kind).isEqualTo(SymbolMoniker.Kind.LOCAL)
        assertThat(moniker(result, text, "value =").kind).isEqualTo(SymbolMoniker.Kind.LOCAL)
        listOf("argument)", "local =", "local +").forEach { marker ->
            assertThat(monikers(result.models.single(), text, marker)).isEmpty()
        }
        XdkAdapter().use { adapter ->
            val uri = "file:///App.x"
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            val failed = text.replace("return local + hidden;", "return missing;")
            assertThat(adapter.compile(uri, failed).success).isFalse()
            val at = position(text, "hidden =")
            assertThat(adapter.findMonikers(uri, at.line, at.column)).isEmpty()
        }
    }

    @Test
    fun `dependency replacement retires old IDs and binary source attachment does not change portable identity`() {
        val source = compile(LIBRARY).artifact!!
        XdkAdapter().use { adapter ->
            val uri = "file:///Consumer.x"
            val at = position(CONSUMER, "pick(1)")
            adapter.replaceDependencies(listOf(source))
            assertThat(adapter.compile(uri, CONSUMER).diagnostics).isEmpty()
            val first = adapter.findMonikers(uri, at.line, at.column).single()
            adapter.replaceDependencies(listOf(XdkDependency.fromBinary(source.bytes())))
            assertThat(adapter.findMonikers(uri, at.line, at.column)).isEmpty()
            assertThat(adapter.compile(uri, CONSUMER).diagnostics).isEmpty()
            assertThat(adapter.findMonikers(uri, at.line, at.column)).containsExactly(first)
            adapter.replaceDependencies(listOf(compile(LIBRARY.replace("= n;", "= n + 1;")).artifact!!))
            assertThat(adapter.findMonikers(uri, at.line, at.column)).isEmpty()
            assertThat(adapter.compile(uri, CONSUMER).diagnostics).isEmpty()
            assertThat(adapter.findMonikers(uri, at.line, at.column).single()).isNotEqualTo(first)
        }
    }

    @Test
    fun `closed graph views preserve import and export kinds and expire after replacement`() {
        val library = directory.resolve("Library.x").also { Files.writeString(it, LIBRARY) }
        val consumer = directory.resolve("Consumer.x").also { Files.writeString(it, CONSUMER) }
        val graph =
            listOf(
                XdkSourceModule("Library", library.toUri().toString()),
                XdkSourceModule("Consumer", consumer.toUri().toString(), setOf("Library")),
            )
        XdkAdapter().use { adapter ->
            adapter.replaceSourceModules(graph)
            val declaration = position(LIBRARY, "pick(Int")
            val use = position(CONSUMER, "pick(1)")
            val exported = adapter.findMonikers(library.toUri().toString(), declaration.line, declaration.column).single()
            assertThat(exported.kind).isEqualTo(SymbolMoniker.Kind.EXPORT)
            assertThat(adapter.findMonikers(consumer.toUri().toString(), use.line, use.column))
                .containsExactly(exported.copy(kind = SymbolMoniker.Kind.IMPORT))
            Files.writeString(library, LIBRARY.replace("= n;", "= n + 1;"))
            assertThat(adapter.findMonikers(consumer.toUri().toString(), use.line, use.column).single().identifier)
                .isNotEqualTo(exported.identifier)
            adapter.replaceSourceModules(emptyList())
            assertThat(adapter.findMonikers(consumer.toUri().toString(), use.line, use.column)).isEmpty()
        }
    }

    @Test
    fun `bundled binary declarations provide stable imported identities without retaining a compiler pool`() {
        val text = "module App { String text = \"hello\"; }"
        val first = compile(text)
        val second = compile(text)
        assertThat(moniker(first, text, "String"))
            .isEqualTo(moniker(second, text, "String"))
            .matches { it.kind == SymbolMoniker.Kind.IMPORT }
    }

    private fun compile(
        text: String,
        uri: String = "file:///Library.x",
        dependencies: List<XdkDependency> = emptyList(),
    ): CompiledSemantics {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val inputs = XdkDependencies(dependencies).open()
        val result = EmbeddingSupport.instance().compileModule(Source(text, uri), inputs.repository, errors)
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        return result.compiledSemantics(inputs, errors)
    }

    private fun moniker(
        result: CompiledSemantics,
        text: String,
        marker: String,
    ) = monikers(result.models.single(), text, marker).single()

    private fun monikers(
        model: SemanticModel,
        text: String,
        marker: String,
    ): List<SymbolMoniker> {
        val at = position(text, marker)
        return model.monikersAt(at.line, at.column)
    }

    private fun position(
        text: String,
        marker: String,
    ): Position {
        val offset = text.indexOf(marker)
        require(offset >= 0) { marker }
        return Position(text.take(offset).count { it == '\n' }, offset - text.lastIndexOf('\n', offset) - 1)
    }

    companion object {
        private val LIBRARY =
            """
            module Library {
                static Int pick(Int n) = n;
                static String pick(String s) = s;
                static <T> T echo(T value) = value;
                class Box<T> {}
            }
            """.trimIndent()
        private val CONSUMER =
            """
            module Consumer {
                package lib import Library;
                Int number() = lib.pick(1);
                String text() = lib.pick("x");
                lib.Box<Int>? box = Null;
                Int echoed() = lib.echo(1);
                String echoedText() = lib.echo("x");
            }
            """.trimIndent()
    }
}
