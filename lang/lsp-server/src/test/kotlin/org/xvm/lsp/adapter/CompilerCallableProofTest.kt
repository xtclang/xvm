package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ErrorList
import org.xvm.compiler.Source
import org.xvm.lsp.adapter.xdk.CompilerRenameFacts
import org.xvm.lsp.adapter.xdk.ProofIdentity
import org.xvm.lsp.adapter.xdk.SemanticModel.SourceLocation
import org.xvm.lsp.adapter.xdk.XdkDependencies
import org.xvm.lsp.adapter.xdk.XdkRename
import org.xvm.lsp.adapter.xdk.projectRenameFacts

/** Stable detached facts must preserve every alternative and back edge across compiler attempts. */
class CompilerCallableProofTest {
    @Test
    fun `fresh union proofs are equivalent but losing a target or its receiver is not`() {
        val text =
            """
            module App {
                class First { Int read() = 1; }
                class Second { Int read() = 2; }
                Int use(First | Second target) = target.read();
            }
            """.trimIndent()
        val before = facts(text)
        val after = facts(text)
        val plan = XdkRename.Plan(mapOf(SOURCE to text), emptyMap())
        assertThat(XdkRename.preservesBindings(before, after, plan)).isTrue()
        val at = readSite(after, text)
        val choice = after.callables.getValue(at) as ProofIdentity.Alternatives
        assertThat(choice.targets).hasSize(2)
        val branches = choice.targets.map { it as ProofIdentity.Composed }
        val lost = choice.copy(targets = setOf(branches.first()))
        val rebound = choice.copy(targets = setOf(branches.first().copy(owner = branches.last().owner), branches.last()))
        val owner = branches.first().owner as ProofIdentity.Source
        val missingSource = owner.copy(location = owner.location.copy(sourceName = null))
        val untranslatable = choice.copy(targets = setOf(branches.first().copy(owner = missingSource), branches.last()))
        listOf(lost, rebound, untranslatable).forEach { damaged ->
            assertThat(XdkRename.preservesBindings(before, replacing(after, at, damaged), plan)).isFalse()
        }
        val unreadable = replacing(after, at, untranslatable)
        assertThat(XdkRename.preservesBindings(unreadable, unreadable, plan)).isFalse()
    }

    @Test
    fun `recursive contract proof retains the closing edge and delegate receiver`() {
        val text =
            """
            module App {
                interface Api { Int read(); }
                class Loop(Loop next) delegates Api(next) {}
                Int use(Loop target) = target.read();
            }
            """.trimIndent()
        val before = facts(text)
        val after = facts(text)
        val plan = XdkRename.Plan(mapOf(SOURCE to text), emptyMap())
        assertThat(XdkRename.preservesBindings(before, after, plan)).isTrue()
        val at = readSite(after, text)
        val route = after.callables.getValue(at) as ProofIdentity.Composed
        assertThat(route.cycles).isNotEmpty()
        assertThat(route.delegates).isNotEmpty()
        listOf(route.copy(cycles = emptyList()), route.copy(delegates = emptyList())).forEach { damaged ->
            assertThat(XdkRename.preservesBindings(before, replacing(after, at, damaged), plan)).isFalse()
        }
    }

    private fun replacing(
        facts: CompilerRenameFacts,
        at: SourceLocation,
        identity: ProofIdentity,
    ) = CompilerRenameFacts(facts.models, facts.constants, facts.methods, facts.properties, callables = facts.callables + (at to identity))

    @Test
    fun `nested delegate alternatives remain part of the call and declaration dispatch proofs`() {
        val text =
            """
            module App {
                interface Api { Int read(); }
                class First implements Api { @Override Int read() = 1; }
                class Second implements Api { @Override Int read() = 2; }
                class Forward(First | Second target) delegates Api(target) {}
                Int use(Forward value) = value.read();
            }
            """.trimIndent()
        val before = facts(text)
        val after = facts(text)
        val plan = XdkRename.Plan(mapOf(SOURCE to text), emptyMap())
        assertThat(XdkRename.preservesBindings(before, after, plan)).isTrue()
        val at = readSite(after, text)
        val route = after.callables.getValue(at) as ProofIdentity.Composed
        val choice = route.alternatives.single() as ProofIdentity.Alternatives
        assertThat(choice.targets).hasSize(2)
        assertThat(route.delegates).isNotEmpty()
        assertThat(after.methods.chains.any { it.alternatives.isNotEmpty() }).isTrue()
        val lost = route.copy(alternatives = listOf(choice.copy(targets = setOf(choice.targets.first()))))
        listOf(lost, route.copy(delegates = emptyList()), route.copy(alternatives = emptyList())).forEach { changed ->
            assertThat(XdkRename.preservesBindings(before, replacing(after, at, changed), plan)).isFalse()
        }
    }

    @Test
    fun `a changed generic argument cannot preserve the callable proof`() {
        val text =
            """
            module App {
                class First<T>(T value) { T read() = value; }
                class Second<T>(T value) { T read() = value; }
                Object use(First<Int> | Second<String> target) = target.read();
            }
            """.trimIndent()
        val before = facts(text)
        val after = facts(text)
        val plan = XdkRename.Plan(mapOf(SOURCE to text), emptyMap())
        assertThat(XdkRename.preservesBindings(before, after, plan)).isTrue()
        val at = readSite(after, text)
        val choice = after.callables.getValue(at) as ProofIdentity.Alternatives
        val branches = choice.targets.map { it as ProofIdentity.Composed }
        val owner = branches.first().owner as ProofIdentity.TypeShape
        val other = branches.last().owner as ProofIdentity.TypeShape
        assertThat(owner.components.last()).isNotEqualTo(other.components.last())
        val substituted = owner.copy(components = listOf(owner.components.first(), other.components.last()))
        val changed = choice.copy(targets = setOf(branches.first().copy(owner = substituted), branches.last()))
        assertThat(XdkRename.preservesBindings(before, replacing(after, at, changed), plan)).isFalse()
    }

    @Test
    fun `annotation constructor arguments participate in callable equivalence`() {
        val text =
            """
            module App {
                class First { Int read() = 1; }
                class Second { Int read() = 2; }
                mixin Mark(String label) into Object {}
                Int use((@Mark("one") First) | (@Mark("two") Second) target) = target.read();
            }
            """.trimIndent()
        val before = facts(text)
        val after = facts(text)
        val plan = XdkRename.Plan(mapOf(SOURCE to text), emptyMap())
        assertThat(XdkRename.preservesBindings(before, after, plan)).isTrue()
        val at = readSite(after, text)
        val choice = after.callables.getValue(at) as ProofIdentity.Alternatives
        val branches = choice.targets.map { it as ProofIdentity.Composed }
        val owner = branches.first().owner as ProofIdentity.TypeShape
        val argument = owner.components.last() as ProofIdentity.Value
        val changedOwner = owner.copy(components = owner.components.dropLast(1) + argument.copy(value = "changed"))
        val changed = choice.copy(targets = setOf(branches.first().copy(owner = changedOwner), branches.last()))
        assertThat(XdkRename.preservesBindings(before, replacing(after, at, changed), plan)).isFalse()
    }

    private fun readSite(
        facts: CompilerRenameFacts,
        text: String,
    ): SourceLocation {
        val prefix = text.take(text.lastIndexOf("read"))
        val line = prefix.count { it == '\n' }
        val column = prefix.substringAfterLast('\n').length
        return SourceLocation(SOURCE, requireNotNull(facts.models.single().occurrenceAt(line, column)).range)
    }

    private fun facts(text: String): CompilerRenameFacts {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        val compilation = EmbeddingSupport.instance().compileModule(Source(text, SOURCE), null, errors)
        assertThat(compilation.succeeded()).describedAs(errors.errors.toString()).isTrue()
        return compilation.projectRenameFacts(XdkDependencies(emptyList()).open(), errors).also {
            assertThat(errors.hasSeriousErrors()).describedAs(errors.errors.toString()).isFalse()
        }
    }

    private companion object {
        const val SOURCE = "App.x"
    }
}
