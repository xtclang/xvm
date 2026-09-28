package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ClassStructure
import org.xvm.asm.ConstantPool
import org.xvm.asm.Constants.Access
import org.xvm.asm.ErrorList
import org.xvm.asm.constants.MethodBody.Implementation
import org.xvm.asm.constants.TypeInfo
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.dispatch

/** Real compiler chains: a body category alone must never manufacture an editable declaration. */
class CompilerDispatchRoutesTest {
    @Test
    fun `written interface default and class override retain their actual source contracts`() {
        inspect(
            "interface Api { Int read() = 1; } class Box implements Api { @Override Int read() = 2; }",
        ) { module, errors ->
            val owner = type(module, "Box", errors)
            val method = owner.methods.values.single { it.identity.name == "read" }
            assertThat(method.chain.map { it.implementation }).contains(Implementation.Explicit, Implementation.Default)
            val route = owner.dispatch(method, errors)
            assertThat(route.supported).isTrue()
            assertThat(route.methods.map { it.namespace.name }).containsExactlyInAnyOrder("Box", "Api")
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
    fun `union type has multiple callable contracts and remains unsupported for rename`() {
        inspect("class First { Int read() = 1; } class Second { Int read() = 2; }") { module, errors ->
            val first = (module.getChild("First") as ClassStructure).formalType
            val second = (module.getChild("Second") as ClassStructure).formalType
            val owner = module.constantPool.ensureUnionTypeConstant(first, second).ensureTypeInfo(errors)
            val method = owner.methods.values.single { it.identity.name == "read" }
            assertThat(method.chain.map { it.implementation }).contains(Implementation.Union)
            assertThat(owner.dispatch(method, errors).supported).isFalse()
        }
    }

    private fun type(
        module: ClassStructure,
        name: String,
        errors: ErrorList,
    ): TypeInfo = (module.getChild(name) as ClassStructure).formalType.ensureAccess(Access.PRIVATE).ensureTypeInfo(errors)

    private fun inspect(
        body: String,
        check: (ClassStructure, ErrorList) -> Unit,
    ) {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val compilation = EmbeddingSupport.instance().compileModule(Source("module Routes { $body }", "Routes.x"), null, errors)
        assertThat(compilation.succeeded()).describedAs(errors.errors.toString()).isTrue()
        ConstantPool.withPool(compilation.pool()).use { check(requireNotNull(compilation.file()).module, errors) }
        assertThat(errors.hasSeriousErrors()).describedAs(errors.errors.toString()).isFalse()
    }
}
