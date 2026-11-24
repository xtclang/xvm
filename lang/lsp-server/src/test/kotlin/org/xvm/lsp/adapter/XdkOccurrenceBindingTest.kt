package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.semanticSnapshot
import org.xvm.lsp.model.Location
import java.nio.file.Path

/**
 * Written names that the semantic model once left without a symbol, which navigation then missed: Find References,
 * highlighting and hover find each, and a rename covers it.
 */
class XdkOccurrenceBindingTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `a local or parameter used for its formal type, as in t1 DataType, is an occurrence of it`() {
        val source =
            """
            module Formals {
                String describe(Type /*parameter*/t0, Object value) {
                    Type /*local*/t1 = /*copied*/t0;
                    /*declared*/t1.DataType typed = value.as(/*cast*/t1.DataType);
                    return typed.toString() + value.as(/*formal*/t0.DataType).toString();
                }
            }
            """.trimIndent()
        withSource("Formals", source) { adapter, uri ->
            val local = listOf(range(source, "local", "t1"), range(source, "declared", "t1"), range(source, "cast", "t1"))
            val at = position(source, "cast")
            assertThat(adapter.findReferences(uri, at.line, at.column, true).map(::range)).containsExactlyElementsOf(local)
            assertThat(adapter.getDocumentHighlights(uri, at.line, at.column).map { it.range }).containsExactlyInAnyOrderElementsOf(local)
            assertThat(adapter.getHoverInfo(uri, at.line, at.column)).contains("t1")
            val renamed = requireNotNull(adapter.rename(uri, at.line, at.column, "t2")) { "No rename of t1" }
            assertThat(apply(source, renamed.changes.getValue(uri))).isEqualTo(source.replace("t1", "t2"))

            // a parameter's rename is the workspace's, which proves it over the module's compile
            val formal = position(source, "formal")
            assertThat(adapter.findReferences(uri, formal.line, formal.column, true).map(::range))
                .containsExactly(range(source, "parameter", "t0"), range(source, "copied", "t0"), range(source, "formal", "t0"))
            val parameter = requireNotNull(adapter.rename(uri, formal.line, formal.column, "t9")) { "No rename of t0" }
            assertThat(apply(source, parameter.changes.getValue(uri))).isEqualTo(source.replace("t0", "t9"))
        }
    }

    @Test
    fun `the _ placeholder is no occurrence, so it no longer refuses the proof of a safe delete in its module`() {
        val source =
            """
            module Placeholders {
                (Int, String) pair() = (1, "one");
                Int size() {
                    (/*assigned*/_, String label) = pair();
                    function Int(Int, Int) second = (/*parameter*/_, n) -> n;
                    return second(0, label.size);
                }
                class Box { Int unused() { return 1; } }
            }
            """.trimIndent()
        withSource("Placeholders", source) { adapter, uri ->
            listOf("assigned", "parameter").map { position(source, it) }.forEach { at ->
                assertThat(adapter.findReferences(uri, at.line, at.column, true)).isEmpty()
                assertThat(adapter.getDocumentHighlights(uri, at.line, at.column)).isEmpty()
            }
            // the proofs of code actions other than a rename refuse every occurrence without a symbol in the modules
            // they compile, and an unused member's deletion was refused in any module with a _ (a marker comment in
            // the member would refuse it too, as its deletion would lose the comment)
            val at = source.positionOf("unused()")
            assertThat(adapter.getCodeActions(uri, Range(at, at), emptyList()).map { it.title })
                .contains("Safely delete member 'unused', unused in Placeholders")
        }
        assertThat(snapshot("Placeholders", source).occurrences).noneMatch { it.name == "_" }
    }

    @Test
    fun `a name of a method's return values declares the value, which hover shows and a rename changes`() {
        val source =
            """
            module Returns {
                (Int /*count*/count, String /*label*/label) pair() = (1, "one");
                conditional (Int /*index*/index, String /*text*/text) find() {
                    return True, 0, "zero";
                }
                Int use() {
                    (Int number, String name) = pair();
                    return number + name.size;
                }
            }
            """.trimIndent()
        withSource("Returns", source) { adapter, uri ->
            val label = range(source, "label", "label")
            val at = position(source, "label")
            assertThat(adapter.findReferences(uri, at.line, at.column, true).map(::range)).containsExactly(label)
            assertThat(adapter.getDocumentHighlights(uri, at.line, at.column).map { it.range }).containsExactly(label)
            assertThat(adapter.getHoverInfo(uri, at.line, at.column)).contains("String label")
            // a conditional method's structure holds its Boolean before the written values
            val text = position(source, "text")
            assertThat(adapter.getHoverInfo(uri, text.line, text.column)).contains("String text")
            val renamed = requireNotNull(adapter.rename(uri, at.line, at.column, "title")) { "No rename of label" }
            assertThat(apply(source, renamed.changes.getValue(uri))).isEqualTo(source.replace("/*label*/label", "/*label*/title"))
        }
    }

    @Test
    fun `this in a lazy property's calc is the instance that holds the property, as in a method of its class`() {
        val source =
            """
            module Receivers {
                class Holder {
                    @Lazy Holder self.calc() = /*first*/this;
                    @Lazy Holder[] pair.calc() = [/*second*/this, /*third*/this];
                    Holder me() = /*method*/this;
                }
            }
            """.trimIndent()
        withSource("Receivers", source) { adapter, uri ->
            // Find References takes every this of the class, as the workspace's navigation joins them by their proof
            // identity, and highlighting those of the method
            val second = position(source, "second")
            val holder = listOf("first", "second", "third", "method").map { range(source, it, "this") }
            assertThat(adapter.findReferences(uri, second.line, second.column, true).map(::range)).containsExactlyElementsOf(holder)
            assertThat(adapter.getDocumentHighlights(uri, second.line, second.column).map { it.range })
                .containsExactlyInAnyOrder(range(source, "second", "this"), range(source, "third", "this"))
            assertThat(adapter.getHoverInfo(uri, second.line, second.column)).contains("Holder")
            val method = position(source, "method")
            assertThat(adapter.findReferences(uri, method.line, method.column, true).map(::range)).containsExactlyElementsOf(holder)
        }
    }

    @Test
    fun `super in a method reference is the method it dispatches to, or the super of a mixin's method, as in a call`() {
        val source =
            """
            module References {
                class Base {
                    Int /*declaration*/value(Int n) = n;
                }
                class Derived extends Base {
                    @Override Int value(Int n) {
                        function Int(Int) f = &/*bound*/super(_);
                        return f(n);
                    }
                }
                mixin Doubling into Base {
                    @Override Int value(Int n) {
                        function Int(Int) f = &/*mixed*/super(_);
                        return f(n) + /*called*/super(n);
                    }
                }
                Int run(Int n) {
                    function Int(Int, Int) /*sum*/add = (a, b) -> a + b;
                    function Int(Int) inc = &/*local*/add(_, 1);
                    return inc(n);
                }
            }
            """.trimIndent()
        withSource("References", source) { adapter, uri ->
            val bound = position(source, "bound")
            val value = listOf(range(source, "declaration", "value"), range(source, "bound", "super"))
            assertThat(adapter.findReferences(uri, bound.line, bound.column, true).map(::range)).containsExactlyElementsOf(value)
            assertThat(adapter.getHoverInfo(uri, bound.line, bound.column)).contains("value(Int n)")
            val declaration = position(source, "declaration")
            val renamed = requireNotNull(adapter.rename(uri, declaration.line, declaration.column, "amount")) { "No rename of value" }
            assertThat(apply(source, renamed.changes.getValue(uri))).isEqualTo(source.replace("value(", "amount("))

            // a mixin's super dispatches to whatever its host has, which a call of it names as the method's super too
            val mixed = position(source, "mixed")
            val supers = listOf(range(source, "mixed", "super"), range(source, "called", "super"))
            assertThat(adapter.findReferences(uri, mixed.line, mixed.column, true).map(::range)).containsExactlyElementsOf(supers)
            assertThat(adapter.getHoverInfo(uri, mixed.line, mixed.column)).contains("super")

            // a local function's, too
            val local = position(source, "local")
            val add = listOf(range(source, "sum", "add"), range(source, "local", "add"))
            assertThat(adapter.findReferences(uri, local.line, local.column, true).map(::range)).containsExactlyElementsOf(add)
            val plus = requireNotNull(adapter.rename(uri, local.line, local.column, "plus")) { "No rename of add" }
            assertThat(apply(source, plus.changes.getValue(uri))).isEqualTo(source.replace("add", "plus"))
        }
    }

    @Test
    fun `a primary constructor's parameter passed in the extends clause is an occurrence of the property it declares`() {
        val source =
            """
            module Header {
                class Base {
                    construct(Int value, Int limit) {}
                }
                class Derived(Int /*declared*/seed, Int limit) extends Base(/*passed*/seed + 1, limit) {
                    Int twice() = /*used*/seed * 2;
                }
            }
            """.trimIndent()
        withSource("Header", source) { adapter, uri ->
            val seed = listOf(range(source, "declared", "seed"), range(source, "passed", "seed"), range(source, "used", "seed"))
            val passed = position(source, "passed")
            assertThat(adapter.findReferences(uri, passed.line, passed.column, true).map(::range)).containsExactlyElementsOf(seed)
            assertThat(
                adapter.getDocumentHighlights(uri, passed.line, passed.column).map { it.range },
            ).containsExactlyInAnyOrderElementsOf(seed)
            assertThat(adapter.findDefinition(uri, passed.line, passed.column)?.let(::range)).isEqualTo(range(source, "declared", "seed"))
            for (from in listOf("passed", "declared")) {
                val at = position(source, from)
                val renamed = requireNotNull(adapter.rename(uri, at.line, at.column, "start")) { "No rename of seed from $from" }
                assertThat(apply(source, renamed.changes.getValue(uri))).isEqualTo(source.replace("seed", "start"))
            }
        }
    }

    @Test
    fun `a name in a static local's initializer in a loop is an occurrence of what it names`() {
        val source =
            """
            module Statics {
                class Lexer {
                    static Int /*declared*/Base = 10;
                    Int scan(Iterator<Int> digits) {
                        Int total = 0;
                        while (Int digit := digits.next()) {
                            static Int Limit = /*used*/Base * 2 - 1;
                            static UInt Max = /*inferred*/MaxValue / 10 - 1;
                            if (total < Limit && total.toUInt() < Max) {
                                total += digit;
                            }
                        }
                        return total;
                    }
                }
            }
            """.trimIndent()
        withSource("Statics", source) { adapter, uri ->
            // the loop validates a clone of its body, whose static locals the compiler folds to constants
            val base = listOf(range(source, "declared", "Base"), range(source, "used", "Base"))
            val declared = position(source, "declared")
            assertThat(adapter.findReferences(uri, declared.line, declared.column, true).map(::range)).containsExactlyElementsOf(base)
            val renamed = requireNotNull(adapter.rename(uri, declared.line, declared.column, "Origin")) { "No rename of Base" }
            assertThat(apply(source, renamed.changes.getValue(uri))).isEqualTo(source.replace("Base", "Origin"))

            // MaxValue resolves through the static local's type, UInt
            val inferred = position(source, "inferred")
            assertThat(adapter.getHoverInfo(uri, inferred.line, inferred.column)).contains("MaxValue")
        }
    }

    @Test
    fun `a local or parameter used as a type, as in new Pair of String and type, is an occurrence of it`() {
        val source =
            """
            module Dynamic {
                const Pair<A, B>(A a) {}
                <Other> conditional Pair<String, Other> narrowed(Type<Other> /*parameter*/type) {
                    if (/*tested*/type.is(Type<String>)) {
                        return True, new Pair<String, /*narrowed*/type>("x");
                    }
                    return False;
                }
                <Other> Pair<String, Other> plain(Type<Other> /*declared*/kind) = new Pair<String, /*plain*/kind>("y");
            }
            """.trimIndent()
        withSource("Dynamic", source) { adapter, uri ->
            val type = listOf(range(source, "parameter", "type"), range(source, "tested", "type"), range(source, "narrowed", "type"))
            val narrowed = position(source, "narrowed")
            assertThat(adapter.findReferences(uri, narrowed.line, narrowed.column, true).map(::range)).containsExactlyElementsOf(type)
            assertThat(
                adapter.findDefinition(uri, narrowed.line, narrowed.column)?.let(::range),
            ).isEqualTo(range(source, "parameter", "type"))
            val renamed = requireNotNull(adapter.rename(uri, narrowed.line, narrowed.column, "target")) { "No rename of type" }
            assertThat(apply(source, renamed.changes.getValue(uri))).isEqualTo(source.replace("type", "target"))

            // without narrowing, the type binding is the parameter's formal type, Other; the name is still the parameter
            val plain = position(source, "plain")
            assertThat(adapter.findReferences(uri, plain.line, plain.column, true).map(::range))
                .containsExactly(range(source, "declared", "kind"), range(source, "plain", "kind"))
        }
    }

    @Test
    fun `a function type's return value names declare its values, which hover shows and a rename changes`() {
        val source =
            """
            module Functions {
                typedef function (String /*name*/name, String /*password*/password)(String) as Callback;
                String call(Callback callback) {
                    (String user, String secret) = callback("realm");
                    return user + secret;
                }
            }
            """.trimIndent()
        withSource("Functions", source) { adapter, uri ->
            val password = range(source, "password", "password")
            val at = position(source, "password")
            assertThat(adapter.findReferences(uri, at.line, at.column, true).map(::range)).containsExactly(password)
            assertThat(adapter.getHoverInfo(uri, at.line, at.column)).contains("String password")
            val name = position(source, "name")
            assertThat(adapter.getHoverInfo(uri, name.line, name.column)).contains("String name")
            val renamed = requireNotNull(adapter.rename(uri, at.line, at.column, "token")) { "No rename of password" }
            assertThat(apply(source, renamed.changes.getValue(uri))).isEqualTo(source.replace("/*password*/password", "/*password*/token"))
        }
    }

    private fun snapshot(
        name: String,
        text: String,
    ): SemanticModel {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val compilation = EmbeddingSupport.instance().compileModule(Source(text, "$name.x"), null, errors)
        assertThat(compilation.succeeded()).describedAs(errors.errors.toString()).isTrue()
        return compilation.semanticSnapshot()
    }

    private fun withSource(
        name: String,
        text: String,
        check: (XdkAdapter, String) -> Unit,
    ) {
        val uri =
            directory
                .resolve("$name.x")
                .toFile()
                .apply { writeText(text) }
                .canonicalFile
                .toURI()
                .toString()
        XdkAdapter().use { adapter ->
            adapter.initializeWorkspace(listOf(directory.toString()))
            assertThat(adapter.compile(uri, text).diagnostics).isEmpty()
            check(adapter, uri)
        }
    }

    /** The position just after the marker comment that names [marker]. */
    private fun position(
        source: String,
        marker: String,
    ): Position = "/*$marker*/".let { comment -> source.positionOf(comment, "").let { Position(it.line, it.column + comment.length) } }

    private fun range(
        source: String,
        marker: String,
        name: String,
    ): Range = position(source, marker).let { Range(it, Position(it.line, it.column + name.length)) }

    private fun range(location: Location): Range =
        Range(Position(location.startLine, location.startColumn), Position(location.endLine, location.endColumn))

    private fun apply(
        text: String,
        edits: List<TextEdit>,
    ): String =
        edits
            .sortedWith(compareByDescending<TextEdit> { it.range.start.line }.thenByDescending { it.range.start.column })
            .fold(text) { current, edit ->
                fun offset(position: Position) = current.splitToSequence('\n').take(position.line).sumOf { it.length + 1 } + position.column
                current.replaceRange(offset(edit.range.start), offset(edit.range.end), edit.newText)
            }
}
