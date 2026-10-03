package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ClassStructure
import org.xvm.asm.ConstantPool
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorList
import org.xvm.asm.MethodStructure
import org.xvm.asm.constants.MethodBody.Implementation
import org.xvm.asm.constants.TypeInfo
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.lsp.adapter.xdk.compilerMethodRelations
import org.xvm.lsp.adapter.xdk.compilerPropertyRelations
import org.xvm.lsp.adapter.xdk.compilerSourceTypes
import org.xvm.lsp.adapter.xdk.dispatch
import org.xvm.lsp.adapter.xdk.methodImplementation

/** Real compiler chains: a body category alone must never manufacture an editable declaration. */
class CompilerDispatchRoutesTest {
    @Test
    fun `rename relations inspect conditional methods and properties on validated concrete hosts`() {
        CompilerTestSupport.configure()
        val text =
            """
            module Routes {
                class Box<T>(T value) incorporates conditional Textual<T extends String> {}
                static mixin Textual<T extends String> into Box<T> {
                    Int size() = value.size;
                    Int length.get() = value.size;
                }
                Int read(Box<String> text) = text.size() + text.length;
                Int unrelated(Box<Int> number) = number.value;
            }
            """.trimIndent()
        val errors = ErrorList()
        val compilation = EmbeddingSupport.instance().compileModule(Source(text, "Routes.x"), null, errors)
        assertThat(compilation.succeeded()).describedAs(errors.errors.toString()).isTrue()

        fun nodes(node: AstNode): List<AstNode> = listOf(node) + node.childNodes().flatMap(::nodes)
        val nodes = nodes(requireNotNull(compilation.parsed()))
        ConstantPool.withPool(compilation.pool()).use {
            val methods = compilerMethodRelations(nodes, errors).chains.filter { it.owner.name == "Box" }
            assertThat(methods).anySatisfy { chain ->
                assertThat(chain.supported).isTrue()
                assertThat(chain.methods.map { it.namespace.name to it.name }).contains("Textual" to "size")
            }
            val properties = compilerPropertyRelations(nodes, errors).chains.filter { it.owner.name == "Box" }
            assertThat(properties).anySatisfy { chain ->
                assertThat(chain.supported).isTrue()
                assertThat(chain.properties.map { it.parentConstant.name to it.name }).contains("Textual" to "length")
            }
            val types = compilerSourceTypes(nodes)
            assertThat(types).doesNotHaveDuplicates()
            assertThat(types).noneMatch { it.isFormalType }
        }
        assertThat(errors.hasSeriousErrors()).describedAs(errors.errors.toString()).isFalse()
    }

    @Test
    fun `capped implementation lookup follows the compiler narrowing method`() {
        inspect(
            "interface Api { Api self(); } class Box implements Api { @Override Box self() = this; }",
        ) { module, errors ->
            val owner = type(module, "Box", errors)
            val capped = owner.methods.values.filter { method -> method.chain.any { it.implementation == Implementation.Capped } }
            assertThat(capped).isNotEmpty()
            capped.forEach { method ->
                assertThat(owner.methodImplementation(method, errors)?.namespace?.name).isEqualTo("Box")
            }
        }
    }

    @Test
    fun `into implementation lookup follows the existing constraint method`() {
        inspect("class Base { Int value() = 1; } mixin Mix into Base {}") { module, errors ->
            val owner = type(module, "Mix", errors)
            val method = owner.methods.values.single { it.identity.name == "value" }
            assertThat(method.chain.map { it.implementation }).contains(Implementation.FromInto)
            assertThat(owner.methodImplementation(method, errors)?.namespace?.name).isEqualTo("Base")
        }
    }

    @Test
    fun `written interface default and class override retain their actual source contracts`() {
        inspect(
            "interface Api { Int read() = 1; } class Box implements Api { @Override Int read() = 2; }",
        ) { module, errors ->
            val owner = type(module, "Box", errors)
            val method = owner.methods.values.single { it.identity.name == "read" }
            assertThat(method.chain.map { it.implementation })
                .contains(Implementation.Explicit, Implementation.Default)
            val route = owner.dispatch(method, errors)
            assertThat(route.supported).isTrue()
            assertThat(route.methods.map { it.namespace.name })
                .containsExactlyInAnyOrder("Box", "Api")
        }
    }

    @Test
    fun `bodyless written class methods retain source contracts without executable targets`() {
        inspect("class Base { Int read(); } class Actual extends Base { @Override Int read() = 1; }") { module, errors ->
            val owner = type(module, "Base", errors)
            val method = owner.methods.values.single { it.identity.name == "read" }
            assertThat(method.chain.map { it.implementation }).contains(Implementation.SansCode)
            val route = owner.dispatch(method, errors)
            assertThat(route.supported).isTrue()
            assertThat(route.methods.map { it.namespace.name to it.name }).containsExactly("Base" to "read")
            assertThat(owner.methodImplementation(method, errors)).isNull()
        }
    }

    @Test
    fun `delegate provenance retains receiver and written interface and implementation`() {
        inspect(
            "interface Api { Int read(); } class Actual implements Api { @Override Int read() = 1; } " +
                "class Box(Actual target) delegates Api(target) {}",
        ) { module, errors ->
            val owner = type(module, "Box", errors)
            val method = owner.methods.values.single { it.identity.name == "read" }
            assertThat(method.chain.map { it.implementation }).contains(Implementation.Delegating)
            val route = owner.dispatch(method, errors)
            assertThat(route.supported).isTrue()
            assertThat(route.delegates.map { it.name }).containsExactly("target")
            assertThat(route.methods.map { it.namespace.name }).contains("Actual", "Api")
        }
    }

    @Test
    fun `union type has multiple callable contracts and cannot be flattened into one chain`() {
        inspect("class First { Int read() = 1; } class Second { Int read() = 2; }") { module, errors ->
            val first = (module.getChild("First") as ClassStructure).formalType
            val second = (module.getChild("Second") as ClassStructure).formalType
            val owner =
                module.constantPool.ensureUnionTypeConstant(first, second).ensureTypeInfo(errors)
            val method = owner.methods.values.single { it.identity.name == "read" }
            assertThat(method.chain.map { it.implementation }).contains(Implementation.Union)
            assertThat(owner.dispatch(method, errors).supported).isFalse()
        }
    }

    @Test
    fun `recursive delegation retains a finite contract and explicit closing edge`() {
        inspect("interface Api { Int read(); } class Loop(Loop next) delegates Api(next) {}") { module, errors ->
            val owner = type(module, "Loop", errors)
            val method = owner.methods.values.single { it.identity.name == "read" }
            val route = owner.dispatch(method, errors)
            assertThat(route.supported).isTrue()
            assertThat(route.methods.map { it.namespace.name to it.name }).containsExactly("Api" to "read")
            assertThat(route.delegates.map { it.name }).containsExactly("next")
            val cycle = route.cycles.single()
            assertThat(cycle.owner.name).isEqualTo("Loop")
            assertThat(cycle.contracts.map { it.namespace.name to it.name }).containsExactly("Api" to "read")
            assertThat(owner.methodImplementation(method, errors)).isNull()
        }
    }

    @Test
    fun `generated shorthand constructors do not become independently editable methods`() {
        inspect("class Box(Int value = 1) {} Box make() = new Box(value = 2);") { module, errors ->
            val owner = type(module, "Box", errors)
            val constructors =
                owner.methods.values.filter {
                    it.isCtorOrValidator && it.chain.any { body -> body.isSynthetic }
                }
            assertThat(constructors).isNotEmpty()
            constructors.forEach { assertThat(owner.dispatch(it, errors).supported).isFalse() }
        }
    }

    @Test
    fun `implicit virtual child constructors remain construction contracts rather than renameable methods`() {
        inspect(
            "class Base { class Child { construct() {} construct(Int value) {} } } " +
                "class Derived extends Base { @Override class Child {} }",
        ) { module, errors ->
            val child = (module.getChild("Derived") as ClassStructure).getChild("Child") as ClassStructure
            val owner = child.formalType.ensureAccess(Access.PRIVATE).ensureTypeInfo(errors)
            val implicit = owner.methods.values.filter { it.chain.any { body -> body.implementation == Implementation.Implicit } }
            // Implicit bodies have no MethodStructure for isCtorOrValidator to classify. Inspect
            // their original declaration identity, without treating it as an executable body here.
            val declarations = implicit.mapNotNull { it.identity.component as? MethodStructure }
            assertThat(declarations.filter { it.isConstructor }).isNotEmpty()
            implicit.forEach { assertThat(owner.dispatch(it, errors).supported).isFalse() }
        }
    }

    @Test
    fun `native binary methods do not acquire written executable implementation targets`() {
        inspect("") { module, errors ->
            val info = module.constantPool.typeString().ensureTypeInfo(errors)
            val native = info.methods.values.filter { method -> method.chain.firstOrNull()?.implementation == Implementation.Native }
            assertThat(native).isNotEmpty()
            native.forEach { method -> assertThat(info.methodImplementation(method, errors)).isNull() }
        }
    }

    private fun type(
        module: ClassStructure,
        name: String,
        errors: ErrorList,
    ): TypeInfo =
        (module.getChild(name) as ClassStructure)
            .formalType
            .ensureAccess(Access.PRIVATE)
            .ensureTypeInfo(errors)

    private fun inspect(
        body: String,
        check: (ClassStructure, ErrorList) -> Unit,
    ) {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val compilation =
            EmbeddingSupport
                .instance()
                .compileModule(Source("module Routes { $body }", "Routes.x"), null, errors)
        assertThat(compilation.succeeded()).describedAs(errors.errors.toString()).isTrue()
        ConstantPool.withPool(compilation.pool()).use {
            check(requireNotNull(compilation.file()).module, errors)
        }
        assertThat(errors.hasSeriousErrors()).describedAs(errors.errors.toString()).isFalse()
    }
}
