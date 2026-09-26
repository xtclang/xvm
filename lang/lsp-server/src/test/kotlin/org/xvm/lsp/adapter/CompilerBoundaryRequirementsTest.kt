package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.asm.FileStructure
import org.xvm.asm.ModuleRepository
import org.xvm.asm.MultiMethodStructure
import org.xvm.compiler.BuildRepository
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.lsp.adapter.xdk.SemanticModel
import org.xvm.lsp.adapter.xdk.semanticSnapshot
import org.xvm.lsp.adapter.xdk.semanticSnapshots
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.reflect.Modifier
import java.time.Instant
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.Executors

class CompilerBoundaryRequirementsTest {
    @ParameterizedTest
    @ValueSource(strings = ["id", "&id", "pick", "&pick"])
    fun `bound generic functions retain binary AST and emit readable artifacts`(reference: String) {
        val result =
            compile("module Boundary { static <T> T id(T value)=value; <T> T pick(T value)=value; function Int(Int) make()=$reference; }")
        val artifact = bytes(result)
        val restored = FileStructure(ByteArrayInputStream(artifact)).module
        assertThat(restored.name).isEqualTo("Boundary")
        assertThat((restored.getChild("make") as MultiMethodStructure).methods().single().ast).isNotNull()
        assertPure(result.semanticSnapshot())
    }

    @Test
    fun `serialized dependency identities match source declarations without conflating overloads or modules`() {
        val library = compile("module Library { static Int pick(Int n)=n; static String pick(String n)=n; }")
        val other = compile("module Other { static Int pick(Int n)=n; }")
        val repository = repository(library, other)
        val source =
            "module Consumer { package lib import Library; package other import Other; " +
                "Int run()=lib.pick(1)+other.pick(2); String text()=lib.pick(\"x\"); }"
        val consumer = compile(source, repository)
        val methods = nodes(library.parsed()).filterIsInstance<MethodDeclarationStatement>().filter { it.nameToken.valueText == "pick" }
        val calls = consumer.callBindings().values.filter { it.method().name == "pick" }
        assertThat(calls).hasSize(3)
        assertThat(calls.map { it.method() }.distinct()).hasSize(3)
        val sourceModel = library.semanticSnapshot()
        val consumerModel = consumer.semanticSnapshot()
        methods.forEach { declaration ->
            val call = calls.single { it.method() == declaration.component.identityConstant }
            assertThat(call.method()).isNotSameAs(declaration.component.identityConstant)
            val name = declaration.nameToken
            val at = SemanticModel.Position(Source.calculateLine(name.startPosition), Source.calculateOffset(name.startPosition))
            val symbol = sourceModel.symbolAt(at.line, at.column)!!
            assertThat(symbol.declarationSource).isEqualTo("file:///Boundary.x")
            assertThat(consumerModel.symbol(symbol.id)).isNull()
        }
        consumerModel.calls.forEach { call ->
            assertThat(consumerModel.symbol(call.method)!!.declaration).isNull()
            assertThat(consumerModel.definitionLocationAt(call.callee.start.line, call.callee.end.column - 1)).isNull()
        }
    }

    @Test
    fun `a fresh dependency repository changes selected types and rejects removed members`() {
        fun dependency(type: String) = compile("module Library { static $type value()=${if (type == "Int") "1" else "\"new\""}; }")
        val text = "module Consumer { package lib import Library; void run() { var value=lib.value(); } }"
        val first = compile(text, repository(dependency("Int"))).semanticSnapshot()
        val second = compile(text, repository(dependency("String"))).semanticSnapshot()

        fun returned(model: SemanticModel): String =
            model
                .type(
                    model.calls
                        .single()
                        .signature.returns
                        .single(),
                )!!
                .displayName
        assertThat(returned(first)).isEqualTo("Int")
        assertThat(returned(second)).isEqualTo("String")
        assertThat(second.symbol(first.calls.single().method)).isNull()
        val errors = ErrorList()
        val removed = repository(compile("module Library { static Int replacement()=1; }"))
        val failed = EmbeddingSupport.instance().compileModule(Source(text, "file:///Boundary.x"), removed, errors)
        assertThat(failed.succeeded()).isFalse()
        assertThat(errors.hasSeriousErrors()).isTrue()
        assertThat(returned(first)).isEqualTo("Int")
    }

    @Test
    fun `semantic inspection preserves emitted bytes and copied snapshots contain no compiler objects`() {
        val source =
            "module Boundary { interface Reader { Int read(); @RO Int value { @Override Int get()=1; } } " +
                "class Value implements Reader { @Override Int read()=1; @Override Int value=2; } " +
                "class Computed implements Reader { @Override Int read()=2; @Override Int value.get()=3; } " +
                "class Forward(Reader target) delegates Reader(target) {} " +
                "class Concrete(Value target) delegates Reader(target) {} " +
                "class Outer(Concrete target) delegates Reader(target) {} " +
                "annotation Tracked<T> into Var<T> { @Override T get()=super(); @Override void set(T value) { super(value); } } " +
                "class Annotated { @Tracked Int value=1; @Lazy Int later.calc()=2; } " +
                "Int run(Int captured) { function Int() fn=()->captured; return fn(); } }"
        val baseline = bytes(compile(source))
        val inspected = compile(source)
        val errors = ErrorList()
        val snapshot = inspected.semanticSnapshots(errors).single()
        assertThat(errors.errors).isEmpty()
        assertThat(bytes(inspected)).containsExactly(*baseline)
        assertPure(snapshot)
    }

    @Test
    fun `retained snapshots remain isolated through repeated replacement and concurrent pool free queries`() {
        val snapshots =
            (0 until 20).map { version ->
                compile("module Boundary { Int run(Int n) { var value=n+$version; return value; } }")
                    .semanticSnapshot()
            }
        assertThat(snapshots.map { it.id }.distinct()).hasSize(20)
        Executors.newFixedThreadPool(4).use { executor ->
            snapshots
                .map { snapshot ->
                    executor.submit {
                        repeat(20) {
                            val local = snapshot.symbols.single { it.name == "value" }
                            assertThat(snapshot.type(local.type!!)!!.displayName).isEqualTo("Int")
                            assertThat(snapshot.occurrences.count { it.symbol == local.id }).isEqualTo(2)
                            assertPure(snapshot)
                        }
                    }
                }.forEach { it.get() }
        }
    }

    private fun compile(
        text: String,
        repository: ModuleRepository? = null,
    ): EmbeddingSupport.Compilation {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val result = EmbeddingSupport.instance().compileModule(Source(text, "file:///Boundary.x"), repository, errors)
        assertThat(result.succeeded()).describedAs(errors.errors.toString()).isTrue()
        return result
    }

    private fun bytes(result: EmbeddingSupport.Compilation): ByteArray {
        // Compilation timestamps deliberately vary; normalize only that metadata before comparison.
        result.module().timestamp = result.pool().ensureTimeConstant(Instant.EPOCH)
        return ByteArrayOutputStream().also { result.file().writeTo(it) }.toByteArray()
    }

    private fun repository(vararg dependencies: EmbeddingSupport.Compilation): ModuleRepository =
        BuildRepository().apply { dependencies.forEach { storeModule(FileStructure(ByteArrayInputStream(bytes(it))).module) } }

    private fun nodes(node: AstNode): List<AstNode> =
        listOf(node) +
            node
                .children()
                .iterator()
                .asSequence()
                .flatMap(::nodes)
                .toList()

    private fun assertPure(root: SemanticModel) {
        val seen = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())

        fun visit(value: Any?) {
            if (value == null || !seen.add(value)) return
            when (value) {
                is String, is Number, is Boolean, is Enum<*>, is UUID -> {
                    Unit
                }

                is Iterable<*> -> {
                    value.forEach(::visit)
                }

                is Map<*, *> -> {
                    value.forEach { (key, item) ->
                        visit(key)
                        visit(item)
                    }
                }

                else -> {
                    assertThat(value.javaClass.name).startsWith(SemanticModel::class.java.name)
                    value.javaClass.declaredFields
                        .filterNot { Modifier.isStatic(it.modifiers) }
                        .forEach {
                            it.isAccessible = true
                            visit(it.get(value))
                        }
                }
            }
        }
        visit(root)
    }
}
