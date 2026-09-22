package org.xvm.lsp.adapter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xvm.api.EmbeddingSupport
import org.xvm.asm.ConstantPool
import org.xvm.asm.ErrorList
import org.xvm.asm.ErrorListener
import org.xvm.asm.MethodStructure
import org.xvm.asm.ModuleRepository
import org.xvm.asm.ModuleStructure
import org.xvm.compiler.Parser
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.Parameter
import java.util.concurrent.atomic.AtomicBoolean

/** A real compiler consumer of the bounded partial-analysis API; no fallback parser or mock. */
class XdkPartialAnalysisTest {
    @Test
    fun `trailing member access resolves the receiver without producing a compiled method`() {
        val text = "module Editing { @Inject Console console; void run() { console."
        val (analysis, errors) = analyze(text)
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.UNEXPECTED_EOF)
        assertThat(analysis.pool()).isPresent()
        val site = analysis.sites().single()
        assertThat(site.isCall).isFalse()
        val receiver = site.receiver.orElseThrow() as NameExpression
        assertThat(receiver.isValidated).isTrue()
        assertThat(receiver.resolvedTarget).isNotNull()
        ConstantPool.withPool(analysis.pool().orElseThrow()).use {
            assertThat(receiver.type.valueString).contains("Console")
        }
        val method = parents(site).filterIsInstance<MethodDeclarationStatement>().first()
        assertThat(method.name).isEqualTo("run")
        assertThat((method.component as MethodStructure).ast).isNull()

        val compileErrors = ErrorList()
        val compilation = EmbeddingSupport.instance().compileModule(Source(text, URI), null, compileErrors)
        assertThat(compilation.succeeded()).isFalse()
        assertThat(compilation.file()).isNull()
        assertThat(compilation.parsed()).isNull()
    }

    @Test
    fun `unfinished calls retain real argument and separator positions and resolve method parameters`() {
        val text = "module Editing { void run(String text) { text.indexOf(\"a,b\", "
        val (analysis, errors) = analyze(text)
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.UNEXPECTED_EOF)
        val site = analysis.sites().single()
        assertThat(site.isCall).isTrue()
        assertThat(Source.calculateOffset(site.operator.startPosition)).isEqualTo(text.indexOf("(\"a,b\""))
        assertThat(site.arguments).hasSize(1)
        assertThat(site.separators.map { Source.calculateOffset(it.startPosition) }).containsExactly(text.lastIndexOf(','))
        val receiver = site.receiver.orElseThrow() as NameExpression
        val method = parents(site).filterIsInstance<MethodDeclarationStatement>().first()
        val parameter = descendants(method).filterIsInstance<Parameter>().first()
        assertThat(receiver.resolvedTarget).isSameAs(parameter.resolvedTarget)
        assertThat(receiver.isValidated).isTrue()
        assertThat(site.arguments.single().isValidated).isTrue()
        assertThat((site.target as NameExpression).resolvedTarget).isNull()
        ConstantPool.withPool(analysis.pool().orElseThrow()).use {
            assertThat(receiver.type.valueString).contains("String")
            assertThat(
                site.arguments
                    .single()
                    .type.valueString,
            ).contains("String")
        }
        assertThat((method.component as MethodStructure).ast).isNull()
    }

    @Test
    fun `unrelated malformed statements prevent partial semantic analysis`() {
        val (analysis, errors) = analyze("module Editing { void run() { Int broken = ; console.")
        assertThat(errors.errors).isNotEmpty()
        assertThat(errors.errors.map { it.code }).doesNotContain("EMB-5")
        assertThat(analysis.pool()).isEmpty()
        assertThat(analysis.sites()).isEmpty()
    }

    @Test
    fun `scope and flow narrowing come from this attempt and survive a later compilation`() {
        val snapshots =
            listOf("String", "Int").map { type ->
                val text = "module Editing { void run(Object value) { if (value.is($type)) { value."
                val (analysis, errors) = analyze(text)
                assertThat(errors.errors.map { it.code }).containsExactly(Parser.UNEXPECTED_EOF)
                val receiver =
                    analysis
                        .sites()
                        .single()
                        .receiver
                        .orElseThrow()
                assertThat(receiver.isValidated && receiver.typeFit.isFit).isTrue()
                val copiedType = ConstantPool.withPool(analysis.pool().orElseThrow()).use { receiver.type.valueString }
                assertThat(copiedType).contains(type)
                copiedType
            }
        assertThat(snapshots[0]).contains("String").doesNotContain("Int")
        assertThat(snapshots[1]).contains("Int").doesNotContain("String")
    }

    @Test
    fun `unknown receiver reports its real error without inventing a type`() {
        val (analysis, errors) = analyze("module Editing { void run() { missing.")
        assertThat(errors.errors.map { it.code }).contains(Parser.UNEXPECTED_EOF).doesNotContain("EMB-5")
        assertThat(errors.errors).anySatisfy { assertThat(it.code).startsWith("COMPILER-") }
        val receiver =
            analysis
                .sites()
                .single()
                .receiver
                .orElseThrow() as NameExpression
        assertThat(receiver.resolvedTarget).isNull()
        assertThat(receiver.isValidated && receiver.typeFit.isFit).isFalse()
    }

    @Test
    fun `nested calls named arguments and UTF-16 positions retain only top-level separators`() {
        for (newline in listOf("\n", "\r\n")) {
            val text =
                "module Editing {$newline void run(String text) {$newline" +
                    "  /* 😀 */ text.indexOf(text.replace(\"x\", \"y\"), start = 2, "
            val (analysis, errors) = analyze(text)
            assertThat(errors.errors.map { it.code }).containsExactly(Parser.UNEXPECTED_EOF)
            val site = analysis.sites().single()
            assertThat(site.arguments).hasSize(2)
            assertThat(site.arguments).allSatisfy { assertThat(it.isValidated && it.typeFit.isFit).isTrue() }
            assertThat(site.separators).hasSize(2)
            val line = text.lines().last()
            assertThat(site.separators.map { Source.calculateOffset(it.startPosition) }).containsExactly(
                line.indexOf(", start"),
                line.lastIndexOf(','),
            )
            assertThat(Source.calculateLine(site.endPosition)).isEqualTo(2)
            assertThat(Source.calculateOffset(site.endPosition)).isEqualTo(line.length)
            assertThat((site.target as NameExpression).resolvedTarget).isNull()
        }
    }

    @Test
    fun `calls without arguments retain the callee and scope but do not select an overload`() {
        for (suffix in listOf("work(", "work(1", "work(1,")) {
            val (analysis, errors) =
                analyze("module Editing { void work(Int n) {} void work(String s) {} void run() { $suffix")
            assertThat(errors.errors.map { it.code }).containsExactly(Parser.UNEXPECTED_EOF)
            val site = analysis.sites().single()
            assertThat((site.target as NameExpression).name).isEqualTo("work")
            assertThat((site.target as NameExpression).resolvedTarget).isNull()
            assertThat(site.receiver).isEmpty()
            assertThat(parents(site).filterIsInstance<MethodDeclarationStatement>().first().name).isEqualTo("run")
            assertThat(site.arguments).hasSize(if ('1' in suffix) 1 else 0)
            assertThat(site.separators).hasSize(if (suffix.endsWith(',')) 1 else 0)
        }
    }

    @Test
    fun `unsupported contexts and lexer failures never enter semantic analysis`() {
        for (body in listOf("return value.", "Int result = value.", "work(value.", "work(value(", "work(\"unterminated")) {
            val (analysis, errors) = analyze("module Editing { void run(String value) { $body")
            assertThat(errors.errors).isNotEmpty()
            assertThat(errors.errors.map { it.code }).doesNotContain("EMB-5")
            assertThat(analysis.pool()).isEmpty()
            assertThat(analysis.sites()).isEmpty()
        }
        val (complete, errors) = analyze("module Editing { void run() {} }")
        assertThat(errors.errors).isEmpty()
        assertThat(complete.sites()).isEmpty()
        assertThat(complete.pool()).isEmpty()
    }

    @Test
    fun `EOF reaches a non-deduplicating host once and cancellation and budgets stop analysis`() {
        CompilerTestSupport.configure()
        val text = "module Editing { void run(String value) { value."
        val delivered = mutableListOf<ErrorListener.ErrorInfo>()
        val analysis = EmbeddingSupport.instance().analyzeIncomplete(Source(text, URI), null, ErrorListener { delivered.add(it) })
        assertThat(delivered.map { it.code }).containsExactly(Parser.UNEXPECTED_EOF)
        assertThat(analysis.pool()).isPresent()

        val errors = ErrorList(ErrorList.FIRST_ERROR)
        val budgeted = EmbeddingSupport.instance().analyzeIncomplete(Source(text, URI), null, errors)
        assertThat(errors.errors).hasSize(1)
        assertThat(budgeted.pool()).isEmpty()

        val cancelled = AtomicBoolean()
        val listener = ErrorListener.cancellable(ErrorListener.collecting { cancelled.set(true) }, cancelled::get)
        val interrupted = EmbeddingSupport.instance().analyzeIncomplete(Source(text, URI), null, listener)
        assertThat(cancelled.get()).isTrue()
        assertThat(interrupted.pool()).isEmpty()
        val alreadyCancelled = EmbeddingSupport.instance().analyzeIncomplete(Source(text, URI), null, listener)
        assertThat(alreadyCancelled.sourceTrees()).isEmpty()
    }

    @Test
    fun `cancellation during repository loading cannot advance into another compiler phase`() {
        CompilerTestSupport.configure()
        val cancelled = AtomicBoolean()
        val errors = ErrorList()
        val input =
            object : ModuleRepository {
                override fun getModuleNames(): Set<String> = emptySet()

                override fun loadModule(name: String): ModuleStructure? {
                    cancelled.set(true)
                    return null
                }

                override fun storeModule(module: ModuleStructure) {
                    error("An input repository must not receive compiled output")
                }
            }
        val analysis =
            EmbeddingSupport.instance().analyzeIncomplete(
                Source("module Editing { void run(String value) { value.", URI),
                input,
                ErrorListener.cancellable(errors, cancelled::get),
            )
        assertThat(cancelled.get()).isTrue()
        assertThat(errors.errors.map { it.code }).containsExactly(Parser.UNEXPECTED_EOF)
        assertThat(analysis.pool()).isEmpty()
        assertThat(
            analysis
                .sites()
                .single()
                .receiver
                .orElseThrow()
                .isValidated,
        ).isFalse()
    }

    private fun analyze(text: String): Pair<EmbeddingSupport.PartialAnalysis, ErrorList> {
        CompilerTestSupport.configure()
        val errors = ErrorList()
        return EmbeddingSupport.instance().analyzeIncomplete(Source(text, URI), null, errors) to errors
    }

    private fun parents(node: AstNode): Sequence<AstNode> = generateSequence(node.parent) { it.parent }

    private fun descendants(node: AstNode): Sequence<AstNode> =
        sequence {
            yield(node)
            for (child in node.children()) yieldAll(descendants(child))
        }

    private companion object {
        const val URI = "untitled:Editing.x"
    }
}
