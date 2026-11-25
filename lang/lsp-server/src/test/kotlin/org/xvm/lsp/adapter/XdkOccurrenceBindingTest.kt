package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.CompilerRenameFacts
import org.xvm.lsp.adapter.xdk.ProofIdentity
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.XdkAdapter
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.projectRenameFacts
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

    @Test
    fun `a reserved name such as this class in a property's accessor is an occurrence, as in a method`() {
        val source =
            """
            module Reserved {
                service Registry {
                    @Unassigned
                    String app {
                        @Override
                        String get() {
                            if (!assigned) {
                                assert /*accessor*/this:class.name.size > 0;
                            }
                            return super();
                        }
                    }
                    String /*declared*/label() = /*method*/this:class.name;
                }
            }
            """.trimIndent()
        withSource("Reserved", source) { adapter, uri ->
            for (marker in listOf("accessor", "method")) {
                val at = position(source, marker)
                assertThat(adapter.getDocumentHighlights(uri, at.line, at.column).map { it.range })
                    .describedAs(marker)
                    .containsExactly(range(source, marker, "this:class"))
                assertThat(adapter.getHoverInfo(uri, at.line, at.column)).describedAs(marker).contains("this:class")
            }
            // a rename in the module is proven beside both
            val declared = position(source, "declared")
            val renamed = requireNotNull(adapter.rename(uri, declared.line, declared.column, "title")) { "No rename of label" }
            assertThat(apply(source, renamed.changes.getValue(uri))).isEqualTo(source.replace("/*declared*/label", "/*declared*/title"))
        }
    }

    @Test
    fun `this Outer in a virtual child is an occurrence of ecstasy's Outer, which a rename of the outer class keeps`() {
        val source =
            """
            module Outers {
                class /*declared*/Host {
                    class Inner {
                        Host up() = this.Outer;
                        Host host() = this.Host;
                    }
                }
            }
            """.trimIndent()
        withSource("Outers", source) { adapter, uri ->
            fun named(written: String): Range =
                source.positionOf(written, written.substringAfter('.')).let { Range(it, Position(it.line, it.column + written.length - 5)) }
            val outer = named("this.Outer").start
            assertThat(adapter.getDocumentHighlights(uri, outer.line, outer.column).map { it.range }).containsExactly(named("this.Outer"))
            assertThat(adapter.getHoverInfo(uri, outer.line, outer.column)).contains("Outer")
            val declared = position(source, "declared")
            assertThat(adapter.findReferences(uri, declared.line, declared.column, true).map(::range))
                .contains(named("this.Host"))
                .doesNotContain(named("this.Outer"))
            val renamed = requireNotNull(adapter.rename(uri, declared.line, declared.column, "Hub")) { "No rename of Host" }
            assertThat(apply(source, renamed.changes.getValue(uri))).isEqualTo(source.replace("Host", "Hub"))
        }
    }

    @Test
    fun `an anonymous class's this and construction are proven, so a rename of its type and a safe delete beside it are`() {
        val source =
            """
            module Anonymous {
                interface /*declared*/Walker {
                    Int step();
                }
                class Box(Int size) {
                    Walker walker() {
                        return new Walker() {
                            Int count = 0;
                            @Override
                            Int step() = this.Box.size + this.count;
                        };
                    }
                    Int unused() {
                        return 1;
                    }
                }
            }
            """.trimIndent()
        withSource("Anonymous", source) { adapter, uri ->
            // the class's construction calls the constructor that the compiler writes for it, a call that spells the
            // type it implements, so its rename was refused
            val declared = position(source, "declared")
            val renamed = requireNotNull(adapter.rename(uri, declared.line, declared.column, "Stepper")) { "No rename of Walker" }
            assertThat(apply(source, renamed.changes.getValue(uri))).isEqualTo(source.replace("Walker", "Stepper"))
            // the proofs of code actions other than a rename compare every binding of the module exactly
            val at = source.positionOf("unused()")
            assertThat(adapter.getCodeActions(uri, Range(at, at), emptyList()).map { it.title })
                .contains("Safely delete member 'unused', unused in Anonymous")
        }
    }

    @Test
    fun `a named argument of an anonymous class's construction binds to the constructor it passes the arguments to`() {
        val source =
            """
            module Overloads {
                class Base {
                    construct(Int /*int*/size) {}
                    construct(Number size) {}
                    Int step() = 1;
                }
                Base small() = new Base(size=5) {
                    @Override
                    Int step() = 2;
                };
                Base any(Number number) = new Base(size=number) {
                    @Override
                    Int step() = 3;
                };
            }
            """.trimIndent()
        // the compiler writes each class a constructor that passes size on to the one of Base that it selected: the
        // name binds to that one's parameter, not to one that any of the class's constructors would share
        val facts = facts("Overloads", source)
        val constructors =
            facts.models
                .flatMap { model ->
                    model.occurrences.filter { it.name == "construct" && it.role == SemanticModel.Role.DECLARATION }
                }.map { facts.constants.getValue(requireNotNull(it.symbol)) }
        val labels =
            facts.models
                .flatMap { model ->
                    model.occurrences.filter { it.name == "size" && it.role == SemanticModel.Role.REFERENCE }
                }.map { facts.constants[requireNotNull(it.symbol)] as ProofIdentity.Parameter }
        assertThat(constructors).hasSize(2)
        assertThat(labels.map { (it.method as ProofIdentity.Composed).members }).containsExactly(
            listOf(constructors[0]),
            listOf(constructors[1]),
        )
        withSource("Overloads", source) { adapter, uri ->
            // a rename of construct(Int)'s size renames the name that binds to it, as for a named class, and not the one
            // that binds to construct(Number)'s
            val at = position(source, "int")
            val renamed = requireNotNull(adapter.rename(uri, at.line, at.column, "n")) { "No rename of size" }
            assertThat(apply(source, renamed.changes.getValue(uri)))
                .isEqualTo(source.replace("/*int*/size", "/*int*/n").replace("Base(size=5)", "Base(n=5)"))
        }
    }

    @Test
    fun `two anonymous classes of one method are distinct identities, as is each one's this`() {
        val source =
            """
            module Twins {
                interface Walker {
                    Int step();
                    Int base() = 0;
                }
                class Pair {
                    Walker[] walkers() = [
                        new Walker() {
                            @Override
                            Int step() = this.base();
                        },
                        new Walker() {
                            @Override
                            Int step() = this.base() + 1;
                        }
                    ];
                }
            }
            """.trimIndent()
        val facts = facts("Twins", source)
        val receivers =
            facts.models.flatMap { model ->
                model.occurrences.filter { it.name == "this" }.map { facts.constants[requireNotNull(it.symbol)] }
            }
        // each this is the receiver of its own class, which the span of the type it constructs places
        assertThat(receivers).hasSize(2).doesNotHaveDuplicates().allMatch { it is ProofIdentity.Receiver }
        assertThat(receivers.map { (it as ProofIdentity.Receiver).owner }).doesNotHaveDuplicates().allMatch {
            it is ProofIdentity.Source
        }
    }

    @Test
    fun `a formal type's child, as Item in Boxed Item, is an occurrence of the formal type its constraint declares`() {
        val source =
            """
            module Children {
                interface Box</*declared*/Item> {
                    Item first();
                }
                <Boxed extends Box> Int count(Boxed box) {
                    Boxed.Item[] items = [box.first()];
                    return items.size;
                }
                class Spare {
                    Int unused() {
                        return 1;
                    }
                }
            }
            """.trimIndent()
        withSource("Children", source) { adapter, uri ->
            val child = source.positionOf("Boxed.Item", "Item")
            val declared = range(source, "declared", "Item")
            assertThat(adapter.findDefinition(uri, child.line, child.column)?.let(::range)).isEqualTo(declared)
            assertThat(adapter.findReferences(uri, declared.start.line, declared.start.column, true).map(::range))
                .contains(Range(child, Position(child.line, child.column + "Item".length)))
            // hover shows the child's type, which the compiler gives the occurrence whatever it binds to
            assertThat(adapter.getHoverInfo(uri, child.line, child.column)).isEqualTo("```xtc\nType<Boxed.Item, Object> Item\n```")
            // the proofs of code actions other than a rename compare every binding of the module exactly
            val at = source.positionOf("unused()")
            assertThat(adapter.getCodeActions(uri, Range(at, at), emptyList()).map { it.title })
                .contains("Safely delete member 'unused', unused in Children")
        }
    }

    @Test
    fun `a formal type's child binds to the formal type of its name, not to one that a contribution maps it to`() {
        val source =
            """
            module Mapped {
                interface Box</*item*/Item> {
                    Item first();
                }
                interface Crate</*e*/E>
                        extends Box<E> {}
                <T extends Crate> Int count(T crate) {
                    T.Item[] items = [];
                    return items.size;
                }
                <B extends Box> Int other(B box) {
                    B.Item[] items = [];
                    return items.size;
                }
            }
            """.trimIndent()
        withSource("Mapped", source) { adapter, uri ->
            // Crate maps Box's Item to its own E, which the compiler resolves T.Item to; the name is Box's Item, which a
            // rename of E does not change
            fun item(written: String) = source.positionOf(written, "Item").let { Range(it, Position(it.line, it.column + 4)) }
            val mapped = item("T.Item")
            assertThat(adapter.findDefinition(uri, mapped.start.line, mapped.start.column)?.let(::range))
                .isEqualTo(range(source, "item", "Item"))
            val e = position(source, "e")
            assertThat(adapter.findReferences(uri, e.line, e.column, true).map(::range)).doesNotContain(mapped)
            val boxed = item("B.Item")
            assertThat(adapter.findReferences(uri, boxed.start.line, boxed.start.column, true).map(::range))
                .contains(mapped, boxed)
            adapter.rename(uri, e.line, e.column, "F")?.let { edit ->
                assertThat(apply(source, edit.changes.getValue(uri))).contains("T.Item")
            }
        }
    }

    @Test
    fun `a call of a method that the runtime implements, on a union receiver, is proven, so a safe delete beside it is`() {
        val source =
            """
            module Unions {
                const Point(Int x, Int y);
                String show(Point|String value) {
                    StringBuffer buf = new StringBuffer();
                    value.appendTo(buf);
                    return buf.toString();
                }
                class Spare {
                    Int unused() {
                        return 1;
                    }
                }
            }
            """.trimIndent()
        withSource("Unions", source) { adapter, uri ->
            // a const's appendTo is the runtime's, which the call's route on Point | String passes through; the proofs
            // of code actions other than a rename compare every binding of the module exactly
            val at = source.positionOf("unused()")
            assertThat(adapter.getCodeActions(uri, Range(at, at), emptyList()).map { it.title })
                .contains("Safely delete member 'unused', unused in Unions")
        }
    }

    @Test
    fun `a rename of a method that a const has from the runtime stays refused, though a union's call of it is proven`() {
        val source =
            """
            module Natives {
                interface Sized {
                    Int /*declared*/estimateStringLength() = 0;
                }
                const Point(Int x, Int y) implements Sized;
                Int size(Point|String value) = value.estimateStringLength();
                Int sized(Sized s) = s.estimateStringLength();
            }
            """.trimIndent()
        // Point's own route for the method, of which Sized's is a member, keeps the runtime's body unsupported, as only a
        // call's route takes it for the contract it implements: XdkRename.methodFamily refuses a rename over it
        val facts = facts("Natives", source)
        val sized = declared(facts, "estimateStringLength").single()
        assertThat(facts.methods.chains.filter { (it.owner as? ProofIdentity.Source)?.name == "Point" && sized in it.members })
            .isNotEmpty
            .allMatch { !it.supported }
        // the rename, applied by hand, compiles, so its refusal is the proof's
        val renamed = source.replace("estimateStringLength() = 0", "estimate() = 0").replace("s.estimateStringLength()", "s.estimate()")
        assertCompiles("Natives", renamed)
        withSource("Natives", source) { adapter, uri ->
            // the runtime implements Point's estimateStringLength, by its name: renamed, Sized's would no longer be
            // Stringable's, and a Point would take Sized's default where it took the runtime's
            val at = position(source, "declared")
            assertThat(adapter.rename(uri, at.line, at.column, "estimate")).isNull()
        }
    }

    @Test
    fun `a call on a union receiver whose legs share one route, as Point's toString, is proven, so a safe delete beside it is`() {
        val source =
            """
            module Nullables {
                const Point(Int x, Int y);
                String show(Point? value) = value.toString();
                class Spare {
                    Int unused() {
                        return 1;
                    }
                }
            }
            """.trimIndent()
        withSource("Nullables", source) { adapter, uri ->
            // Point? is Point | Nullable, whose toString the compiler selects as one route, Object's, through both
            val at = source.positionOf("unused()")
            assertThat(adapter.getCodeActions(uri, Range(at, at), emptyList()).map { it.title })
                .contains("Safely delete member 'unused', unused in Nullables")
        }
    }

    @Test
    fun `a formal type of the class an annotation is into, named in its method, is an occurrence of that formal type`() {
        val source =
            """
            module Implicit {
                class Holder</*declared*/Inner> {
                    Inner get(Inner i) = i;
                }
                annotation Cap into Holder {
                    @Override
                    Inner get(Inner i) {
                        /*local*/Inner held = super(i);
                        return held;
                    }
                }
                interface Processor<Message> {
                    static annotation Dedup into Processor {}
                }
                class Spare {
                    Int unused() {
                        return 1;
                    }
                }
            }
            """.trimIndent()
        withSource("Implicit", source) { adapter, uri ->
            // the compiler adds Inner to Cap, and Message to Dedup, as a formal type of its own, which no source declares
            val local = position(source, "local")
            val declared = range(source, "declared", "Inner")
            assertThat(adapter.findDefinition(uri, local.line, local.column)?.let(::range)).isEqualTo(declared)
            assertThat(adapter.findReferences(uri, declared.start.line, declared.start.column, true).map(::range))
                .contains(range(source, "local", "Inner"))
            val at = source.positionOf("unused()")
            assertThat(adapter.getCodeActions(uri, Range(at, at), emptyList()).map { it.title })
                .contains("Safely delete member 'unused', unused in Implicit")
        }
    }

    @Test
    fun `a formal type that several contributions give a mixin binds to the first's, whose rename stays refused`() {
        val source =
            """
            module Shared {
                class Holder</*holder*/Inner> {}
                annotation Base</*base*/Inner> {}
                annotation Cap into Holder extends Base {
                    Int peek(Object value) {
                        /*local*/Inner? held = value.is(Inner) ? value : Null;
                        return held == Null ? 0 : 1;
                    }
                }
            }
            """.trimIndent()
        // Inner binds to Holder's, the first contribution's; renamed, Holder's gives Cap a formal type Item, and Inner
        // binds to Base's instead: that compiles, so the rename's refusal is not the compiler's
        val renamed = source.replace("/*holder*/Inner", "/*holder*/Item")
        assertCompiles("Shared", renamed)
        withSource("Shared", source) { adapter, uri ->
            val local = position(source, "local")
            assertThat(adapter.findDefinition(uri, local.line, local.column)?.let(::range)).isEqualTo(range(source, "holder", "Inner"))
            val at = position(source, "holder")
            assertThat(adapter.rename(uri, at.line, at.column, "Item")).isNull()
        }
    }

    @Test
    fun `the methods of an annotation into a union are proven by the union's, so a safe delete beside it is`() {
        val source =
            """
            module Targets {
                class /*declared*/Box {
                    Int /*size*/size() = 1;
                }
                class Bag {
                    Int size() = 2;
                }
                annotation Sized into Box | Bag {}
                annotation Marked into Class | Method {}
                class Spare {
                    Int unused() {
                        return 1;
                    }
                }
            }
            """.trimIndent()
        // Sized's chain for size holds the union's merged method, proven by each leg's route to its declaration
        val facts = facts("Targets", source)
        val sizes = declared(facts, "size")
        val merged =
            facts.methods.chains
                .filter { (it.owner as? ProofIdentity.Source)?.name == "Sized" }
                .flatMap { it.members }
                .filterIsInstance<ProofIdentity.Alternatives>()
                .map { alternatives -> alternatives.targets.mapTo(hashSetOf()) { (it as ProofIdentity.Composed).members } }
        assertThat(sizes).hasSize(2)
        assertThat(merged).contains(sizes.mapTo(hashSetOf()) { listOf(it) })
        // the rename of Box's size, applied by hand, compiles, so its refusal is the proof's
        assertCompiles("Targets", source.replace("/*size*/size()", "/*size*/length()"))
        withSource("Targets", source) { adapter, uri ->
            // the compiler merges the methods of Box | Bag, as size, into methods of the union itself, which no source
            // declares, and each annotation's dispatch chains hold them
            val at = source.positionOf("unused()")
            assertThat(adapter.getCodeActions(uri, Range(at, at), emptyList()).map { it.title })
                .contains("Safely delete member 'unused', unused in Targets")
            // a rename compares the chains with each leg's declarations where the edit moves them
            val declared = position(source, "declared")
            val renamed = requireNotNull(adapter.rename(uri, declared.line, declared.column, "Crate")) { "No rename of Box" }
            assertThat(apply(source, renamed.changes.getValue(uri))).isEqualTo(source.replace("Box", "Crate"))
            // renamed, Box's size would leave the union's, and Sized would lose the method: the rename stays refused
            val size = position(source, "size")
            assertThat(adapter.rename(uri, size.line, size.column, "length")).isNull()
        }
    }

    /** The identities of the declarations of [name] in [facts]' sources, in the order they declare them. */
    private fun declared(
        facts: CompilerRenameFacts,
        name: String,
    ): List<ProofIdentity> =
        facts.models
            .flatMap { model -> model.occurrences.filter { it.name == name && it.role == SemanticModel.Role.DECLARATION } }
            .map { facts.constants.getValue(requireNotNull(it.symbol)) }

    /** That [text], a module compiled alone, compiles without an error. */
    private fun assertCompiles(
        name: String,
        text: String,
    ) {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val compilation = EmbeddingSupport.instance().compileModule(Source(text, "$name.x"), null, errors)
        assertThat(compilation.succeeded() && !errors.hasSeriousErrors()).describedAs(errors.errors.toString()).isTrue()
    }

    /** The rename facts of [text], a module compiled alone, as a proof over its module takes them. */
    private fun facts(
        name: String,
        text: String,
    ): CompilerRenameFacts {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val compilation = EmbeddingSupport.instance().compileModule(Source(text, "$name.x"), null, errors)
        assertThat(compilation.succeeded()).describedAs(errors.errors.toString()).isTrue()
        return compilation.projectRenameFacts(XdkDependencies(emptyList()).open(), errors)
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
