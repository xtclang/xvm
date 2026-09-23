package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.semanticSnapshots
import org.xvm.lsp.model.Location
import java.nio.file.Path
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS

class XdkSemanticLookupTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `generic selected returns navigate to the inferred source type and not the formal`() {
        val source =
            """
            module Lookups {
                class /*item*/Item {}
                <T> T identity(T value) = value;
                void run(Item value) { /*call*/identity(value); }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            assertThat(types(adapter, source, "call")).containsExactly(location(source, "item", "Item"))
        }
    }

    @Test
    fun `covariant source overrides remain distinct from unrelated return types`() {
        val source =
            """
            module Lookups {
                class Value {}
                class Detail extends Value {}
                class Base { Value /*base*/make() = new Value(); }
                class Child extends Base { @Override Detail /*child*/make() = new Detail(); }
                class Unrelated { Detail make() = new Detail(); }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            assertThat(implementations(adapter, source, "base"))
                .containsExactly(location(source, "base", "make"), location(source, "child", "make"))
        }
    }

    @Test
    fun `lookup results retain their source snapshot and stale closed document queries return nothing`() {
        val source = "module Lookups { class /*item*/Item {} void run(Item value) { /*use*/value.toString(); } }"
        withSource(source) { adapter ->
            val old = types(adapter, source, "use")
            adapter.closeDocument(URI)
            assertThat(types(adapter, source, "use")).isEmpty()
            assertThat(implementations(adapter, source, "item")).isEmpty()
            assertThat(adapter.compile(URI, "\n$source").success).isTrue()
            assertThat(types(adapter, "\n$source", "use")).containsExactly(location("\n$source", "item", "Item"))
            assertThat(old).containsExactly(location(source, "item", "Item"))
        }
    }

    @Test
    fun `type definitions follow nominal parameterized nullable and aliased value types`() {
        val source =
            """
            module Lookups {
                class /*box*/Box<T> {}
                class /*item*/Item {}
                typedef Box<Item> as /*alias*/Items;
                void run(Box<Item> box, Box<Item>? optional, Items alias) {
                    /*boxUse*/box.toString();
                    /*nullableUse*/optional.toString();
                    /*aliasUse*/alias.toString();
                }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            for (use in listOf("boxUse", "nullableUse", "aliasUse")) {
                assertThat(types(adapter, source, use)).containsExactly(location(source, "box", "Box"))
            }
            assertThat(types(adapter, source, "alias")).containsExactly(location(source, "alias", "Items"))
        }
    }

    @Test
    fun `type definitions follow narrowing union operands and selected call return types`() {
        val source =
            """
            module Lookups {
                class /*first*/First {}
                class /*second*/Second {}
                First /*declaration*/make() = new First();
                void run(First|Second value) {
                    /*union*/value.toString();
                    if (value.is(First)) { /*narrowed*/value.toString(); }
                    /*call*/make();
                }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            assertThat(types(adapter, source, "union"))
                .containsExactly(location(source, "first", "First"), location(source, "second", "Second"))
            for (use in listOf("narrowed", "call", "declaration")) {
                assertThat(types(adapter, source, use)).containsExactly(location(source, "first", "First"))
            }
        }
    }

    @Test
    fun `unresolved formals lead to their parameter declaration instead of their constraint`() {
        val source =
            """
            module Lookups {
                class Box</*formal*/T> {
                    T echo(T value) = /*use*/value;
                }
                </*methodFormal*/U> U echo(U value) = /*methodUse*/value;
            }
            """.trimIndent()
        withSource(source) { adapter ->
            assertThat(types(adapter, source, "use")).containsExactly(location(source, "formal", "T"))
            assertThat(types(adapter, source, "methodUse")).containsExactly(location(source, "methodFormal", "U"))
        }
    }

    @Test
    fun `library types and unresolved names have no invented source target`() {
        val source = "module Lookups { void run(String value) { /*use*/value.toString(); } }"
        withSource(source) { adapter ->
            assertThat(types(adapter, source, "use")).isEmpty()
            val broken = source.replace("/*use*/value", "/*use*/missing")
            assertThat(adapter.compile(URI, broken).success).isFalse()
            assertThat(types(adapter, broken, "use")).isEmpty()
        }
    }

    @Test
    fun `type implementations are transitive concrete source types and exclude unrelated shapes`() {
        val source =
            """
            module Lookups {
                interface /*api*/Named { String name(); }
                @Abstract class Partial implements Named {}
                class /*base*/Base extends Partial { @Override String name() = "base"; }
                class /*child*/Child extends Base {}
                class Unrelated { String name() = "other"; }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            assertThat(implementations(adapter, source, "api"))
                .containsExactly(location(source, "base", "Base"), location(source, "child", "Child"))
        }
    }

    @Test
    fun `method implementations use generic override chains and exclude overloads and unrelated names`() {
        val source =
            """
            module Lookups {
                interface Mapper<T> { T /*api*/map(T value); }
                class TextMapper implements Mapper<String> {
                    @Override String /*base*/map(String value) = value;
                    String map(Int value) = value.toString();
                }
                class Child extends TextMapper { @Override String /*child*/map(String value) = value; }
                class Inherited extends TextMapper {}
                class Unrelated { String map(String value) = value; }
                void run(Mapper<String> mapper) { mapper. /*call*/map("value"); }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            for (use in listOf("api", "call", "base")) {
                assertThat(implementations(adapter, source, use))
                    .describedAs(use)
                    .containsExactly(location(source, "base", "map"), location(source, "child", "map"))
            }
            assertThat(implementations(adapter, source, "child")).containsExactly(location(source, "child", "map"))
        }
    }

    @Test
    fun `default interface bodies are source implementations and inherited results are deduplicated`() {
        val source =
            """
            module Lookups {
                interface Named { String /*default*/name() = "default"; }
                class First implements Named {}
                class Second extends First {}
                class Other implements Named { @Override String /*override*/name() = "other"; }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            assertThat(implementations(adapter, source, "default"))
                .containsExactly(location(source, "default", "name"), location(source, "override", "name"))
        }
    }

    @Test
    fun `mixin method targets come from the final host composition without standalone diagnostics`() {
        val source =
            """
            module Lookups {
                class Base { String /*base*/name() = "base"; }
                mixin Loud into Base { @Override String /*mixin*/name() = "loud"; }
                mixin Unused into Base { @Override String name() = "unused"; }
                class Host extends Base incorporates Loud {}
            }
            """.trimIndent()
        withSource(source) { adapter ->
            assertThat(adapter.getCachedResult(URI)!!.diagnostics).isEmpty()
            assertThat(implementations(adapter, source, "base"))
                .containsExactly(location(source, "base", "name"), location(source, "mixin", "name"))
        }
    }

    @Test
    fun `anonymous implementation bodies retain captured source type targets`() {
        val source =
            """
            module Lookups {
                class /*item*/Item {}
                interface Named { Item /*api*/name(); }
                Named make(Item value) {
                    return new Named() {
                        @Override Item /*implementation*/name() = /*capture*/value;
                    };
                }
            }
            """.trimIndent()
        withSource(source) { adapter ->
            assertThat(types(adapter, source, "capture")).containsExactly(location(source, "item", "Item"))
            assertThat(implementations(adapter, source, "api")).containsExactly(location(source, "implementation", "name"))
        }
    }

    @Test
    fun `cancelled implementation inspection publishes no partial implementation set`() {
        CompilerTestSupport.configure()
        val source = "module Lookups { interface /*api*/Named {} class Item implements Named {} }"
        val errors = ErrorList()
        val compilation = EmbeddingSupport.instance().compileModule(Source(source, URI), null, errors)
        assertThat(compilation.succeeded()).isTrue()
        val cancelled = ErrorListener.cancellable(errors) { true }
        val model = compilation.semanticSnapshots(cancelled).single()
        val at = position(source, "api")
        assertThat(model.implementationLocationsAt(at.line, at.column)).isEmpty()
        assertThat(errors.errors).isEmpty()
    }

    @Test
    fun `closed member targets and unsaved edits share the current module snapshot`() {
        directory = directory.toRealPath()
        val root = directory.resolve("Lookups.x").toFile()
        val member = directory.resolve("Lookups/Child.x").toFile()
        val source = "module Lookups { interface /*api*/Named { String /*method*/name(); } void run(Child child) { /*use*/child.name(); } }"
        root.writeText(source)
        member.parentFile.mkdirs()
        val original = "class /*child*/Child implements Named { @Override String /*impl*/name() = \"child\"; }"
        member.writeText(original)
        val rootUri = root.toURI().toString()
        val memberUri = member.toURI().toString()
        XdkAdapter().use { adapter ->
            assertThat(adapter.compile(rootUri, source).diagnostics).isEmpty()
            assertThat(types(adapter, source, "use", rootUri)).containsExactly(location(original, "child", "Child", memberUri))
            assertThat(implementations(adapter, source, "method", rootUri))
                .containsExactly(location(original, "impl", "name", memberUri))
            val changed = "\n\n$original"
            assertThat(adapter.compile(memberUri, changed).diagnostics).isEmpty()
            assertThat(types(adapter, source, "use", rootUri)).containsExactly(location(changed, "child", "Child", memberUri))
            assertThat(implementations(adapter, source, "method", rootUri))
                .containsExactly(location(changed, "impl", "name", memberUri))
            assertThat(adapter.compile(memberUri, "class Child {").success).isFalse()
            assertThat(types(adapter, source, "use", rootUri)).isEmpty()
            assertThat(implementations(adapter, source, "api", rootUri)).isEmpty()
            adapter.closeDocument(memberUri)
            assertThat(adapter.compile(rootUri, source).diagnostics).isEmpty()
            assertThat(types(adapter, source, "use", rootUri)).containsExactly(location(original, "child", "Child", memberUri))
        }
    }

    @Test
    fun `copied lookups survive on another thread without a constant pool`() {
        CompilerTestSupport.configure()
        val source =
            "module Lookups { interface Named {} class /*type*/Item implements /*api*/Named {} " +
                "void run(Item value) { /*use*/value.toString(); } }"
        val errors = ErrorList()
        val compilation = EmbeddingSupport.instance().compileModule(Source(source, URI), null, errors)
        assertThat(compilation.succeeded()).describedAs(errors.errors.toString()).isTrue()
        val passive = compilation.semanticSnapshots().single()
        val inspected = compilation.semanticSnapshots(errors).single()
        assertThat(errors.errors).isEmpty()
        val use = position(source, "use")
        val api = position(source, "api")
        Executors.newSingleThreadExecutor().use { executor ->
            executor
                .submit {
                    assertThat(passive.typeDefinitionLocationsAt(use.line, use.column)).hasSize(1)
                    assertThat(passive.implementationLocationsAt(api.line, api.column)).isEmpty()
                    assertThat(inspected.implementationLocationsAt(api.line, api.column)).hasSize(1)
                }.get(10, SECONDS)
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

    private fun types(
        adapter: XdkAdapter,
        source: String,
        marker: String,
        uri: String = URI,
    ): List<Location> {
        val position = position(source, marker)
        return adapter.findTypeDefinitions(uri, position.line, position.column)
    }

    private fun implementations(
        adapter: XdkAdapter,
        source: String,
        marker: String,
        uri: String = URI,
    ): List<Location> {
        val position = position(source, marker)
        return adapter.findImplementation(uri, position.line, position.column)
    }

    private fun position(
        source: String,
        marker: String,
    ): Position {
        val token = "/*$marker*/"
        val offset = source.indexOf(token).also { check(it >= 0) } + token.length
        return Position(source.take(offset).count { it == '\n' }, offset - source.lastIndexOf('\n', offset - 1) - 1)
    }

    private fun location(
        source: String,
        marker: String,
        name: String,
        uri: String = URI,
    ): Location {
        val start = position(source, marker)
        return Location(uri, start.line, start.column, start.line, start.column + name.length)
    }

    private companion object {
        const val URI = "file:///Lookups.x"
    }
}
