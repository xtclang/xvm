package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.lsp.adapter.xdk.XdkAdapter
import java.nio.file.Path

class XdkCallHierarchyTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `selected overloads group repeated call sites and recursive edges retain exact owners`() {
        val source =
            """
            module Calls {
                Int /*integer*/pick(Int value) = value;
                String /*string*/pick(String value) = value;
                Int /*run*/run() { Int n = /*one*/pick(1); return /*two*/pick(n); }
                Int /*recursive*/recursive(Int n) { if (n == 0) { return 0; } return recursive(n - 1); }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            val target = prepare(adapter, source, "integer")
            val incoming = adapter.getIncomingCalls(target).single()
            assertThat(incoming.from.name).isEqualTo("run")
            assertThat(incoming.fromRanges).containsExactly(range(source, "one", "pick"), range(source, "two", "pick"))
            val outgoing = adapter.getOutgoingCalls(prepare(adapter, source, "run")).single()
            assertThat(outgoing.to).isEqualTo(target)
            assertThat(outgoing.fromRanges).isEqualTo(incoming.fromRanges)
            assertThat(adapter.getIncomingCalls(prepare(adapter, source, "string"))).isEmpty()
            val recursive = prepare(adapter, source, "recursive")
            assertThat(adapter.getIncomingCalls(recursive).single().from).isEqualTo(recursive)
            assertThat(adapter.getOutgoingCalls(recursive).single().to).isEqualTo(recursive)
        }
    }

    @Test
    fun `lambda calls belong to the lambda and dynamic function invocation is not guessed`() {
        val source =
            """
            module Calls {
                Int /*leaf*/leaf(Int value) = value;
                Int /*outer*/run(Int captured) {
                    function Int(Int) fn = (Int n) -> /*call*/leaf(n + captured);
                    return fn(1);
                }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            val leaf = prepare(adapter, source, "leaf")
            val caller = adapter.getIncomingCalls(leaf).single().from
            assertThat(caller.name).isEqualTo("<lambda>")
            assertThat(caller.range.start.line).isEqualTo(3)
            assertThat(adapter.getOutgoingCalls(caller).single().to).isEqualTo(leaf)
            assertThat(adapter.getOutgoingCalls(prepare(adapter, source, "outer"))).isEmpty()
        }
    }

    @Test
    fun `anonymous method calls do not become calls from the factory`() {
        val source =
            """
            module Calls {
                interface Reader { Int read(); }
                static Int /*leaf*/leaf() = 1;
                Reader /*factory*/make() {
                    return new Reader() { @Override Int /*body*/read() = leaf(); };
                }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            val incoming = adapter.getIncomingCalls(prepare(adapter, source, "leaf")).single()
            assertThat(incoming.from.selectionRange).isEqualTo(range(source, "body", "read"))
            assertThat(adapter.getOutgoingCalls(prepare(adapter, source, "factory"))).isEmpty()
        }
    }

    @Test
    fun `interface calls record the static selected declaration rather than possible runtime overrides`() {
        val source =
            """
            module Calls {
                interface Named { String /*interface*/name(); }
                class Value implements Named { @Override String /*override*/name() = "value"; }
                String /*caller*/run(Named value) = value.name();
            }
            """.trimIndent()
        withSource(source) { adapter ->
            val outgoing = adapter.getOutgoingCalls(prepare(adapter, source, "caller")).single()
            assertThat(outgoing.to).isEqualTo(prepare(adapter, source, "interface"))
            assertThat(adapter.getIncomingCalls(prepare(adapter, source, "override"))).isEmpty()
        }
    }

    @Test
    fun `closed member callers use their own source and old hierarchy items expire after edits`() {
        directory = directory.toRealPath()
        val root = directory.resolve("Calls.x").toFile()
        val member = directory.resolve("Calls/Worker.x").toFile()
        val source = "module Calls { static Int /*target*/target(Int n) = n; }"
        root.writeText(source)
        member.parentFile.mkdirs()
        member.writeText("class Worker { Int run() = target(1); }")
        XdkAdapter().use { adapter ->
            val uri = root.toURI().toString()
            assertThat(adapter.compile(uri, source).diagnostics).isEmpty()
            val target = prepare(adapter, source, "target", uri)
            val caller = adapter.getIncomingCalls(target).single().from
            assertThat(caller.uri).isEqualTo(member.toURI().toString())
            assertThat(
                adapter
                    .getOutgoingCalls(caller)
                    .single()
                    .to.uri,
            ).isEqualTo(uri)
            assertThat(adapter.compile(uri, "\n$source").diagnostics).isEmpty()
            assertThat(adapter.getIncomingCalls(target)).isEmpty()
            assertThat(adapter.getOutgoingCalls(caller)).isEmpty()
            val current = prepare(adapter, "\n$source", "target", uri)
            assertThat(adapter.getIncomingCalls(current)).hasSize(1)
            assertThat(adapter.compile(member.toURI().toString(), "class Worker {").success).isFalse()
            assertThat(adapter.getIncomingCalls(current)).isEmpty()
        }
    }

    private fun withSource(
        source: String,
        test: (XdkAdapter) -> Unit,
    ) {
        XdkAdapter().use { adapter ->
            val result = adapter.compile(URI, source)
            assertThat(result.success).describedAs(result.diagnostics.toString()).isTrue()
            test(adapter)
        }
    }

    private fun prepare(
        adapter: XdkAdapter,
        source: String,
        marker: String,
        uri: String = URI,
    ): CallHierarchyItem {
        val at = range(source, marker, "").start
        return adapter.prepareCallHierarchy(uri, at.line, at.column).single()
    }

    private fun range(
        source: String,
        marker: String,
        name: String,
    ): Range {
        val token = "/*$marker*/"
        val offset = source.indexOf(token).also { check(it >= 0) } + token.length
        val line = source.take(offset).count { it == '\n' }
        val column = offset - source.lastIndexOf('\n', offset - 1) - 1
        return Range(Position(line, column), Position(line, column + name.length))
    }

    private companion object {
        const val URI = "file:///Calls.x"
    }
}
